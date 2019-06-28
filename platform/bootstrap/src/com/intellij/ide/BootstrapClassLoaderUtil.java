// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.idea.Main;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class BootstrapClassLoaderUtil {
  public static final String CLASSPATH_ORDER_FILE = "classpath-order.txt";

  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";
  private static final String MARKETPLACE_PLUGIN_DIR = "marketplace";

  private BootstrapClassLoaderUtil() { }

  private static Logger getLogger() {
    return Logger.getInstance(BootstrapClassLoaderUtil.class);
  }

  @NotNull
  public static ClassLoader initClassLoader() throws MalformedURLException {
    Collection<String> jarOrder = loadJarOrder();

    Collection<URL> classpath = new LinkedHashSet<>();
    addParentClasspath(classpath, false);
    addIDEALibraries(classpath, jarOrder);
    addAdditionalClassPath(classpath);
    addParentClasspath(classpath, true);

    File mpBoot = new File(PathManager.getPluginsPath(), MARKETPLACE_PLUGIN_DIR + "/lib/boot/marketplace-bootstrap.jar");
    boolean installMarketplace = mpBoot.exists();
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
      .logJarAccess(jarOrder.isEmpty())
      .useCache();
    if (Boolean.valueOf(System.getProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, "true"))) {
      builder.allowBootstrapResources();
    }

    ClassLoaderUtil.addPlatformLoaderParentIfOnJdk9(builder);

    if (installMarketplace) {
      try {
        List<BytecodeTransformer> transformers = new SmartList<>();
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

  /**
   * A version of PathManager.getSystemPath() with no dependencies on external classes (to avoid classloading)
   */
  private static String getSystemPath() {
    String systemPath = System.getProperty(PathManager.PROPERTY_SYSTEM_PATH);
    if (systemPath != null) {
      if (systemPath.length() >= 3 && systemPath.startsWith("\"") && systemPath.endsWith("\"")) {
        systemPath = systemPath.substring(1, systemPath.length() - 1);
      }
      if (systemPath.startsWith("~/") || systemPath.startsWith("~\\")) {
        systemPath = System.getProperty("user.home") + systemPath.substring(1);
      }
    }
    else{
      String pathSelector = System.getProperty(PathManager.PROPERTY_PATHS_SELECTOR);
      if (pathSelector != null) {
        systemPath = PathManager.getDefaultSystemPathFor(pathSelector);
      }
    }
    return systemPath;
  }


  private static void addParentClasspath(Collection<? super URL> classpath, boolean ext) throws MalformedURLException {
    if (!SystemInfo.IS_AT_LEAST_JAVA9) {
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
            // We need to add these paths in the order specified in order.txt, so don't add them at this stage
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
    else if (!ext) {
      parseClassPathString(ManagementFactory.getRuntimeMXBean().getClassPath(), classpath);
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

  private static void addIDEALibraries(Collection<? super URL> classpath, Collection<String> jarOrder) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRoot != null;
    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    classpath.add(selfRootUrl);

    File libFolder = new File(PathManager.getLibPath());

    for (String jarName : jarOrder) {
      File jarFile = new File(libFolder, jarName);
      if (jarFile.exists()) {
        classpath.add(jarFile.toURI().toURL());
      }
    }

    addLibraries(classpath, libFolder, selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static Collection<String> loadJarOrder() {
    try {
      Path path = Paths.get(getSystemPath(), CLASSPATH_ORDER_FILE);
      if (path.toFile().exists()) {
        return new LinkedHashSet<>(Files.readAllLines(path));
      }
    }
    catch (IOException ignored) {
    }
    return Collections.emptyList();
  }

  private static void addLibraries(Collection<? super URL> classPath, File fromDir, URL selfRootUrl) throws MalformedURLException {
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

  private static void addAdditionalClassPath(Collection<? super URL> classpath) {
    parseClassPathString(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH), classpath);
  }

  private static void parseClassPathString(String pathString, Collection<? super URL> classpath) {
    if (pathString != null && !pathString.isEmpty()) {
      try {
        String libPath = PathManager.getLibPath();
        StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
        while (tokenizer.hasMoreTokens()) {
          String pathItem = tokenizer.nextToken();
          if (!pathItem.startsWith(libPath)) {
            // We need to add paths from lib directory in the order specified in order.txt, so don't add them at this stage
            classpath.add(new File(pathItem).toURI().toURL());
          }
        }
      }
      catch (MalformedURLException e) {
        getLogger().error(e);
      }
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

    TransformingLoader(@NotNull Builder builder, List<BytecodeTransformer> transformers) {
      super(builder);
      myTransformers = Collections.unmodifiableList(transformers);
    }

    @Override
    protected Class _defineClass(String name, byte[] b) {
      return super._defineClass(name, doTransform(name, null, b));
    }

    @Override
    protected Class _defineClass(String name, byte[] b, @Nullable ProtectionDomain protectionDomain) {
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
}