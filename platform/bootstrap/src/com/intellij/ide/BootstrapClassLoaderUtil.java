// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.idea.Main;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

public final class BootstrapClassLoaderUtil {
  public static final String CLASSPATH_ORDER_FILE = "classpath-order.txt";

  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";
  private static final String MARKETPLACE_PLUGIN_DIR = "marketplace";

  private BootstrapClassLoaderUtil() { }

  private static Logger getLogger() {
    return Logger.getInstance(BootstrapClassLoaderUtil.class);
  }

  public static @NotNull ClassLoader initClassLoader() throws MalformedURLException {
    List<String> jarOrder = loadJarOrder();

    Collection<URL> classpath = new LinkedHashSet<>();
    addParentClasspath(classpath, false);
    addIdeaLibraries(classpath, jarOrder);
    addAdditionalClassPath(classpath);
    addParentClasspath(classpath, true);

    File mpBoot = new File(PathManager.getPluginsPath(), MARKETPLACE_PLUGIN_DIR + "/lib/boot/marketplace-bootstrap.jar");
    boolean installMarketplace = shouldInstallMarketplace(mpBoot);
    if (installMarketplace) {
      File marketplaceImpl = new File(PathManager.getPluginsPath(), MARKETPLACE_PLUGIN_DIR + "/lib/boot/marketplace-impl.jar");
      if (marketplaceImpl.exists()) {
        classpath.add(marketplaceImpl.toURI().toURL());
      }
    }

    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .urls(filterClassPath(new ArrayList<>(classpath)))
      .allowLock()
      .usePersistentClasspathIndexForLocalClassDirectories()
      .logJarAccess(Boolean.getBoolean("idea.log.classpath.info"))
      .autoAssignUrlsWithProtectionDomain()
      .useCache();
    if (Boolean.parseBoolean(System.getProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, "true"))) {
      builder.allowBootstrapResources();
    }

    ClassLoaderUtil.addPlatformLoaderParentIfOnJdk9(builder);

    if (installMarketplace) {
      try {
        List<BytecodeTransformer> transformers = new ArrayList<>();
        UrlClassLoader spiLoader = UrlClassLoader.build().urls(mpBoot.toURI().toURL()).parent(BootstrapClassLoaderUtil.class.getClassLoader()).get();
        for (BytecodeTransformer transformer : ServiceLoader.load(BytecodeTransformer.class, spiLoader)) {
          transformers.add(transformer);
        }
        if (!transformers.isEmpty()) {
          return new TransformingLoader(builder, transformers);
        }
      }
      catch (Throwable e) {
        // at this point logging is not initialized yet, so reporting the error directly
        String path = new File(PathManager.getPluginsPath(), MARKETPLACE_PLUGIN_DIR).getAbsolutePath();
        String message = "As a workaround, you may uninstall or update JetBrains Marketplace Support plugin at " + path;
        Main.showMessage("JetBrains Marketplace boot failure", new Exception(message, e));
      }
    }

    return builder.get();
  }

  private static boolean shouldInstallMarketplace(File mpBoot) {
    if (!mpBoot.exists()) {
      return false;
    }
    try {
      Path homePath = Paths.get(PathManager.getHomePath());
      SimpleVersion ideVersion = null;
      try (BufferedReader reader = Files.newBufferedReader(homePath.resolve("build.txt"))) {
        ideVersion = SimpleVersion.parse(reader.readLine());
      }
      catch (IOException ignored){
      }
      if (ideVersion == null && SystemInfoRt.isMac) {
        try (BufferedReader reader = Files.newBufferedReader(homePath.resolve("Resources/build.txt"))) {
          ideVersion = SimpleVersion.parse(reader.readLine());
        }
      }
      if (ideVersion != null) {
        SimpleVersion sinceVersion = null;
        SimpleVersion untilVersion = null;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(PathManager.getPluginsPath()).resolve(MARKETPLACE_PLUGIN_DIR).resolve("platform-build.txt"))) {
          sinceVersion = SimpleVersion.parse(reader.readLine());
          untilVersion = SimpleVersion.parse(reader.readLine());
        }
        catch (IOException ignored) {
        }
        return ideVersion.isCompatible(sinceVersion, untilVersion);
      }
    }
    catch (Throwable ignored) {
    }
    return true;
  }

  private static void addParentClasspath(Collection<URL> classpath, boolean ext) throws MalformedURLException {
    if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
      if (!ext) {
        parseClassPathString(System.getProperty("java.class.path"), classpath);
      }
    }
    else {
      String[] extDirs = System.getProperty("java.ext.dirs", "").split(File.pathSeparator);
      if (ext && extDirs.length == 0) return;

      List<URLClassLoader> loaders = new ArrayList<>(2);
      for (ClassLoader loader = BootstrapClassLoaderUtil.class.getClassLoader(); loader != null; loader = loader.getParent()) {
        if (loader instanceof URLClassLoader) {
          loaders.add(0, (URLClassLoader)loader);
        }
        else {
          getLogger().warn("Unknown class loader: " + loader.getClass().getName());
        }
      }

      String libPath = PathManager.getLibPath();
      for (URLClassLoader loader : loaders) {
        URL[] urls = loader.getURLs();
        for (URL url : urls) {
          String path = urlToPath(url);
          if (path.startsWith(libPath)) {
            // we need to add these paths in the order specified in order.txt, so don't add them at this stage
            continue;
          }

          boolean isExt = false;
          for (String extDir : extDirs) {
            if (path.startsWith(extDir) && path.length() > extDir.length() && path.charAt(extDir.length()) == File.separatorChar) {
              isExt = true;
              break;
            }
          }

          if (isExt == ext) {
            classpath.add(url);
          }
        }
      }
    }
  }

  private static String urlToPath(URL url) throws MalformedURLException {
    try {
      return new File(url.toURI().getSchemeSpecificPart()).getPath();
    }
    catch (URISyntaxException e) {
      throw new MalformedURLException(url.toString());
    }
  }

  private static void addIdeaLibraries(Collection<URL> classpath, Collection<String> jarOrder) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRoot != null;
    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    File libFolder = new File(PathManager.getLibPath());
    for (String jarName : jarOrder) {
      if (jarName == null || jarName.isEmpty()) {
        continue;
      }

      File jarFile = new File(libFolder, jarName);
      if (jarFile.exists()) {
        classpath.add(jarFile.toURI().toURL());
      }
    }

    classpath.add(selfRootUrl);
    addLibraries(classpath, libFolder, selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static List<String> loadJarOrder() {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") InputStream resource = BootstrapClassLoaderUtil.class.getResourceAsStream(CLASSPATH_ORDER_FILE);
    if (resource != null) {
      try (BufferedReader stream = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
        return FileUtilRt.loadLines(stream);
      }
      catch (Exception ignored) { }  // skip, we can load the app
    }
    return Collections.emptyList();
  }

  private static void addLibraries(Collection<URL> classPath, File fromDir, URL selfRootUrl) throws MalformedURLException {
    File[] files = fromDir.listFiles();
    if (files == null) return;

    for (File file : files) {
      if (FileUtilRt.isJarOrZip(file)) {
        URL url = file.toURI().toURL();
        if (!selfRootUrl.equals(url)) {
          classPath.add(url);
        }
      }
    }
  }

  private static void addAdditionalClassPath(Collection<URL> classpath) {
    parseClassPathString(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH), classpath);
  }

  private static void parseClassPathString(String pathString, Collection<URL> classpath) {
    if (pathString == null || pathString.isEmpty()) {
      return;
    }

    try {
      String libPath = PathManager.getLibPath();
      StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        if (!pathItem.startsWith(libPath)) {
          // we need to add paths from lib directory in the order specified in order.txt, so don't add them at this stage
          classpath.add(new File(pathItem).toURI().toURL());
        }
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static List<URL> filterClassPath(List<URL> classpath) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<URL> i = classpath.iterator(); i.hasNext(); ) {
        String url = i.next().toExternalForm();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return classpath;
  }

  private static class TransformingLoader extends UrlClassLoader {
    private final List<BytecodeTransformer> myTransformers;

    TransformingLoader(Builder builder, List<BytecodeTransformer> transformers) {
      super(builder);
      myTransformers = Collections.unmodifiableList(transformers);
    }

    @Override
    protected Class<?> _defineClass(String name, byte[] b) {
      return super._defineClass(name, doTransform(name, null, b));
    }

    @Override
    protected Class<?> _defineClass(String name, byte[] b, @Nullable ProtectionDomain protectionDomain) {
      return super._defineClass(name, doTransform(name, protectionDomain, b), protectionDomain);
    }

    private byte[] doTransform(String name, ProtectionDomain protectionDomain, byte[] bytes) {
      byte[] b = bytes;
      for (BytecodeTransformer transformer : myTransformers) {
        byte[] result = transformer.transform(this, name, protectionDomain, b);
        if (result != null) {
          b = result;
        }
      }
      return b;
    }
  }

  private static final class SimpleVersion implements Comparable<SimpleVersion>{
    private final int myMajor;
    private final int myMinor;

    SimpleVersion(int major, int minor) {
      myMajor = major;
      myMinor = minor;
    }

    public boolean isAtLeast(@NotNull SimpleVersion ver) {
      return ver.compareTo(this) <= 0;
    }

    public boolean isCompatible(@Nullable SimpleVersion since, @Nullable SimpleVersion until) {
      if (since != null && until != null) {
        return compareTo(since) >= 0 && compareTo(until) <= 0;
      }
      if (since != null) {
        return isAtLeast(since);
      }
      if (until != null) {
        return until.isAtLeast(this);
      }
      return true; // assume compatible of nothing is specified
    }
    
    @Override
    public int compareTo(@NotNull SimpleVersion ver) {
      return myMajor != ver.myMajor? Integer.compare(myMajor, ver.myMajor) : Integer.compare(myMinor, ver.myMinor);
    }

    @Nullable
    public static SimpleVersion parse(@Nullable String text) {
      if (!StringUtil.isEmpty(text)) {
        try {
          text = text.trim();
          int dash = text.lastIndexOf('-');
          if (dash >= 0) {
            text = text.substring(dash + 1); // strip product code
          }
          int dot = text.indexOf('.');
          if (dot >= 0) {
            return new SimpleVersion(Integer.parseInt(text.substring(0, dot)), parseMinor(text.substring(dot + 1)));
          }
          return new SimpleVersion(Integer.parseInt(text), 0);
        }
        catch (NumberFormatException ignored) {
        }
      }
      return null;
    }

    private static int parseMinor(String text) {
      try {
        if ("*".equals(text) || "SNAPSHOT".equals(text)) {
          return Integer.MAX_VALUE;
        }
        final int dot = text.indexOf('.');
        return Integer.parseInt(dot >= 0 ? text.substring(0, dot) : text);
      }
      catch (NumberFormatException e) {
      }
      return 0;
    }
  }
}