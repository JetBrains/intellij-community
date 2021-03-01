// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.idea.Main;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BootstrapClassLoaderUtil {
  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";
  private static final @NonNls String MARKETPLACE_PLUGIN_DIR = "marketplace";

  private BootstrapClassLoaderUtil() { }

  private static Logger getLogger() {
    return Logger.getInstance(BootstrapClassLoaderUtil.class);
  }

  public static @NotNull PathClassLoader initClassLoader() throws IOException {
    Path distDir = Path.of(PathManager.getHomePath());
    if (Boolean.getBoolean("idea.use.dev.build.server")) {
      ClassLoader classLoader = BootstrapClassLoaderUtil.class.getClassLoader();
      if (!(classLoader instanceof PathClassLoader)) {
        //noinspection SpellCheckingInspection,UseOfSystemOutOrSystemErr
        System.err.println("Please run with VM option -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader");
        System.exit(1);
      }

      List<Path> paths = loadClassPathFromDevBuildServer(distDir);
      PathClassLoader result = (PathClassLoader)classLoader;
      result.getClassPath().appendFiles(paths);
      return result;
    }

    Collection<Path> classpath = new LinkedHashSet<>();
    parseClassPathString(System.getProperty("java.class.path"), classpath);
    addIdeaLibraries(distDir.resolve("lib"), classpath);
    parseClassPathString(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH), classpath);

    Path pluginDir = Path.of(PathManager.getPluginsPath());
    Path marketPlaceBootDir = pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("lib/boot");
    Path mpBoot = marketPlaceBootDir.resolve("marketplace-bootstrap.jar");
    boolean installMarketplace = shouldInstallMarketplace(distDir, mpBoot);
    if (installMarketplace) {
      Path marketplaceImpl = marketPlaceBootDir.resolve("marketplace-impl.jar");
      if (Files.exists(marketplaceImpl)) {
        classpath.add(marketplaceImpl);
      }
    }

    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .files(filterClassPath(classpath))
      .usePersistentClasspathIndexForLocalClassDirectories()
      .autoAssignUrlsWithProtectionDomain()
      .parent(ClassLoader.getPlatformClassLoader())
      .useCache();
    if (Boolean.parseBoolean(System.getProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, "true"))) {
      builder.allowBootstrapResources();
    }

    if (installMarketplace) {
      try {
        PathClassLoader spiLoader = new PathClassLoader(UrlClassLoader.build()
                                                          .files(Collections.singletonList(mpBoot))
                                                          .parent(BootstrapClassLoaderUtil.class.getClassLoader()));
        Iterator<BytecodeTransformer> transformers = ServiceLoader.load(BytecodeTransformer.class, spiLoader).iterator();
        return new PathClassLoader(builder, transformers.hasNext() ? transformers.next() : null);
      }
      catch (Throwable e) {
        // at this point logging is not initialized yet, so reporting the error directly
        String path = pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).toString();
        String message = "As a workaround, you may uninstall or update JetBrains Marketplace Support plugin at " + path;
        Main.showMessage(BootstrapBundle.message("bootstrap.error.title.jetbrains.marketplace.boot.failure"), new Exception(message, e));
      }
    }

    return new PathClassLoader(builder);
  }

  private static List<Path> loadClassPathFromDevBuildServer(@NotNull Path distDir) throws IOException {
    String platformPrefix = System.getProperty("idea.platform.prefix", "idea");
    URL serverUrl = new URL("http://127.0.0.1:20854/build?platformPrefix=" + platformPrefix);
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println("Waiting for " + serverUrl + " (first launch can take up to 1-2 minute)");
    HttpURLConnection connection = (HttpURLConnection)serverUrl.openConnection();
    connection.setConnectTimeout(10_000);
    // 5 minutes should be enough even for full build
    connection.setReadTimeout(5 * 60_000);
    int responseCode;
    try {
      responseCode = connection.getResponseCode();
    }
    catch (ConnectException e) {
      throw new RuntimeException("Please run Dev Build Server", e);
    }

    connection.disconnect();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new RuntimeException("Dev Build server not able to handle build request, see server's log for details");
    }

    List<Path> result = new ArrayList<>();
    FileSystem fs = FileSystems.getDefault();
    Path excludedModuleListPath = distDir.resolve("out/dev-run/" + platformPrefix + "/libClassPath.txt");
    try (Stream<String> lineStream = Files.lines(excludedModuleListPath)) {
      lineStream.forEach(s -> {
        if (!s.isEmpty()) {
          result.add(fs.getPath(s));
        }
      });
    }
    return result;
  }

  private static boolean shouldInstallMarketplace(@NotNull Path homePath, @NotNull Path mpBoot) {
    if (!Files.exists(mpBoot)) {
      return false;
    }

    try {
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

  private static void addIdeaLibraries(@NotNull Path libDir, @NotNull Collection<Path> classpath) throws IOException {
    Path classPathFile = libDir.resolve("classpath.txt");
    try (Stream<String> stream = Files.lines(classPathFile)) {
      stream.forEach(jarName -> {
        if (!jarName.isEmpty()) {
          classpath.add(libDir.resolve(jarName));
        }
      });
      return;
    }
    catch (NoSuchFileException ignored) {
    }
    catch (Exception e) {
      getLogger().error("Cannot read " + classPathFile + ": ", e);
    }

    // no classpath file - compute classpath
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRootPath != null;
    Path selfRoot = Paths.get(selfRootPath);
    classpath.add(selfRoot);
    Path libFolder = Paths.get(PathManager.getLibPath());
    addLibraries(classpath, libFolder, selfRoot);
    addLibraries(classpath, libFolder.resolve("ant/lib"), null);
  }

  private static void addLibraries(Collection<Path> classPath, Path fromDir, @Nullable Path selfRoot) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(fromDir)) {
      for (Path file : dirStream) {
        String path = file.toString();
        int lastDotIndex = path.length() - 4;
        if (lastDotIndex > 0 &&
            path.charAt(lastDotIndex) == '.' &&
            (path.regionMatches(true, lastDotIndex + 1, "jar", 0, 3) || path.regionMatches(true, lastDotIndex + 1, "zip", 0, 3))) {
          if (selfRoot == null || !selfRoot.equals(file)) {
            classPath.add(file);
          }
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
  }

  private static void parseClassPathString(@Nullable String pathString, @NotNull Collection<Path> classpath) {
    if (pathString == null || pathString.isEmpty()) {
      return;
    }

    String libPath = PathManager.getLibPath();
    StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
    while (tokenizer.hasMoreTokens()) {
      String pathItem = tokenizer.nextToken();
      if (!pathItem.startsWith(libPath)) {
        // we need to add paths from lib directory in the order specified in order.txt, so don't add them at this stage
        classpath.add(Paths.get(pathItem));
      }
    }
  }

  private static @NotNull List<Path> filterClassPath(@NotNull Collection<Path> classpath) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<Path> i = classpath.iterator(); i.hasNext(); ) {
        String url = i.next().toString();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return new ArrayList<>(classpath);
  }

  private static final class SimpleVersion implements Comparable<SimpleVersion>{
    private final int myMajor;
    private final int myMinor;

    SimpleVersion(int major, int minor) {
      myMajor = major;
      myMinor = minor;
    }

    public boolean isAtLeast(@NotNull Comparable<? super SimpleVersion> ver) {
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

    public static @Nullable SimpleVersion parse(@Nullable String text) {
      if (text == null || text.isEmpty()) {
        return null;
      }

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
      catch (NumberFormatException ignored) {
      }
      return 0;
    }
  }
}
