// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.BytecodeTransformer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class BootstrapClassLoaderUtil {
  private static final @NonNls String MARKETPLACE_PLUGIN_DIR = "marketplace";
  private static final String MARKETPLACE_BOOTSTRAP_JAR = "marketplace-bootstrap.jar";

  private BootstrapClassLoaderUtil() { }

  // for CWM
  // Marketplace plugin, PROPERTY_IGNORE_CLASSPATH and PROPERTY_ADDITIONAL_CLASSPATH is not supported by intention
  public static @NotNull Collection<Path> getProductClassPath() throws IOException {
    Path distDir = Path.of(PathManager.getHomePath());
    if (AppMode.isDevServer()) {
      return loadClassPathFromDevBuild(distDir);
    }

    Path libDir = distDir.resolve("lib");
    Collection<Path> classpath = new LinkedHashSet<>();

    parseClassPathString(System.getProperty("java.class.path"), classpath);

    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRootPath != null;
    Path selfRoot = Path.of(selfRootPath);
    classpath.add(selfRoot);
    addLibraries(classpath, libDir, selfRoot);
    addLibraries(classpath, libDir.resolve("ant/lib"), null);
    return classpath;
  }

  public static void initClassLoader(boolean addCwmLibs) throws Throwable {
    Path distDir = Path.of(PathManager.getHomePath());
    ClassLoader classLoader = BootstrapClassLoaderUtil.class.getClassLoader();
    if (!(classLoader instanceof PathClassLoader)) {
      throw new RuntimeException("You must run JVM with -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader");
    }
    PathClassLoader pathClassLoader = (PathClassLoader)classLoader;

    if (AppMode.isDevServer()) {
      List<Path> paths = loadClassPathFromDevBuild(distDir);
      pathClassLoader.getClassPath().appendFiles(paths);
      return;
    }

    // Non-unified classloader:
    // - launcher is run with bootstrap classpath;
    // - launcher computes classpath in runtime and replaces the classloader.
    // Unified classloader:
    // - classpath is generated during build,
    // - classpath is used as is.
    // TODO remove non-unified classloader

    Collection<Path> classpath = new LinkedHashSet<>();

    Path preinstalledPluginDir = distDir.resolve("plugins");
    Path pluginDir = preinstalledPluginDir;
    Path marketPlaceBootDir = findMarketplaceBootDir(pluginDir);
    Path mpBoot = marketPlaceBootDir.resolve(MARKETPLACE_BOOTSTRAP_JAR);
    if (Files.notExists(mpBoot)) {
      pluginDir = Path.of(PathManager.getPluginsPath());
      marketPlaceBootDir = findMarketplaceBootDir(pluginDir);
      mpBoot = marketPlaceBootDir.resolve(MARKETPLACE_BOOTSTRAP_JAR);
    }

    boolean installMarketplace = shouldInstallMarketplace(distDir, mpBoot);
    if (installMarketplace) {
      Path marketplaceImpl = marketPlaceBootDir.resolve("marketplace-impl.jar");
      if (Files.exists(marketplaceImpl)) {
        classpath.add(marketplaceImpl);
      }
    }

    boolean updateSystemClassLoader = false;
    if (addCwmLibs) {
      // Remote dev requires Projector libraries in system classloader due to AWT internals (see below)
      // At the same time, we don't want to ship them with base (non-remote) IDE due to possible unwanted interference with plugins
      // See also: com.jetbrains.codeWithMe.projector.PluginClassPathRuntimeCustomizer
      String relativeLibPath = "cwm-plugin-projector/lib/projector";
      Path remoteDevPluginLibs = preinstalledPluginDir.resolve(relativeLibPath);
      boolean exists = Files.exists(remoteDevPluginLibs);
      if (!exists) {
        remoteDevPluginLibs = Path.of(PathManager.getPluginsPath(), relativeLibPath);
        exists = Files.exists(remoteDevPluginLibs);
      }

      if (exists) {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(remoteDevPluginLibs)) {
          // add all files in that dir except for plugin jar
          for (Path f : dirStream) {
            if (f.toString().endsWith(".jar")) {
              classpath.add(f);
            }
          }
        }
      }

      // AWT can only use builtin and system class loaders to load classes,
      // so set the system loader to something that can find projector libs
      updateSystemClassLoader = true;
    }

    if (!classpath.isEmpty()) {
      pathClassLoader.getClassPath().appendFiles(List.copyOf(classpath));
    }

    if (installMarketplace) {
      try {
        PathClassLoader spiLoader = new PathClassLoader(UrlClassLoader.build()
                                                          .files(Collections.singletonList(mpBoot))
                                                          .parent(classLoader));
        Iterator<BytecodeTransformer> transformers = ServiceLoader.load(BytecodeTransformer.class, spiLoader).iterator();
        if (transformers.hasNext()) {
          pathClassLoader.setTransformer(new BytecodeTransformerAdapter(transformers.next()));
        }
      }
      catch (Throwable e) {
        // at this point logging is not initialized yet, so reporting the error directly
        String path = pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).toString();
        String message = "As a workaround, you may uninstall or update JetBrains Marketplace Support plugin at " + path;
        StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.jetbrains.marketplace.boot.failure"), new Exception(message, e));
      }
    }

    if (updateSystemClassLoader) {
      Class<ClassLoader> aClass = ClassLoader.class;
      MethodHandles.privateLookupIn(aClass, MethodHandles.lookup()).findStaticSetter(aClass, "scl", aClass).invoke(pathClassLoader);
    }
  }

  private static @NotNull Path findMarketplaceBootDir(Path pluginDir) {
    return pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("lib/boot");
  }

  private static @NotNull List<Path> loadClassPathFromDevBuild(@NotNull Path distDir) throws IOException {
    String platformPrefix = System.getProperty("idea.platform.prefix", "idea");
    Path devRunDir = distDir.resolve("out/dev-run");
    Path productDevRunDir = devRunDir.resolve(AppMode.getDevBuildRunDirName(platformPrefix));
    Path coreClassPathFile = productDevRunDir.resolve("core-classpath.txt");
    FileSystem fs = FileSystems.getDefault();
    try {
      return loadCoreClassPath(fs, coreClassPathFile);
    }
    catch (NoSuchFileException ignore) {
    }

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
      throw new RuntimeException("Please run Dev Build Server. Run build/dev-build-server.cmd in OS terminal. See https://bit.ly/3rMPnUX",
                                 e);
    }

    connection.disconnect();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new RuntimeException("Dev Build server is not able to handle build request, see server's log for details");
    }

    return loadCoreClassPath(fs, productDevRunDir.resolve("libClassPath.txt"));
  }

  private static @NotNull List<Path> loadCoreClassPath(FileSystem fs, Path excludedModuleListPath) throws IOException {
    List<Path> result = new ArrayList<>();
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
    if (Files.notExists(mpBoot)) {
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
        try (BufferedReader reader = Files.newBufferedReader(Path.of(PathManager.getPluginsPath()).resolve(MARKETPLACE_PLUGIN_DIR).resolve("platform-build.txt"))) {
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

    StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
    while (tokenizer.hasMoreTokens()) {
      classpath.add(Path.of(tokenizer.nextToken()));
    }
  }

  private static final class SimpleVersion implements Comparable<SimpleVersion>{
    private final int myMajor;
    private final int myMinor;

    private SimpleVersion(int major, int minor) {
      myMajor = major;
      myMinor = minor;
    }

    private boolean isAtLeast(@NotNull Comparable<? super SimpleVersion> ver) {
      return ver.compareTo(this) <= 0;
    }

    private boolean isCompatible(@Nullable SimpleVersion since, @Nullable SimpleVersion until) {
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

    private static @Nullable SimpleVersion parse(@Nullable String text) {
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

  private static final class BytecodeTransformerAdapter implements PathClassLoader.BytecodeTransformer {
    private final BytecodeTransformer impl;

    private BytecodeTransformerAdapter(BytecodeTransformer impl) {
      this.impl = impl;
    }

    @Override
    public boolean isApplicable(String className, ClassLoader loader) {
      return impl.isApplicable(className, loader, null);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, byte[] classBytes) {
      return impl.transform(loader, className, null, classBytes);
    }
  }
}
