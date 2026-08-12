// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

import com.intellij.platform.devIdeConfig.DevIdeConfig;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Launcher for IDE pre-built from sources.
 * <p>
 * Separates IDE runtime phase from the build phase, which is useful for containerized
 * environments where the build happens on the host system and the IDE runs inside a container.
 * <p>
 * Reads configuration from a file specified by the "idea.ide.config.path" system property. The value is either a path or,
 * under Bazel, a runfiles-relative one - see {@link DevIdeConfig#resolveConfigFile}.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
@ApiStatus.Internal
public final class PreBuiltDevMain {
  public static void main(String[] args) throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    if (!(DevMainKt.class.getClassLoader() instanceof PathClassLoader classLoader)) {
      System.err.println("The current class loader is not a com.intellij.util.lang.PathClassLoader.");
      return;
    }

    Path configFile = DevIdeConfig.declaredConfigFile();
    if (configFile == null) {
      throw new IllegalStateException("System property '" + DevIdeConfig.CONFIG_PATH_PROPERTY + "' is not set");
    }
    DevIdeConfig.Content ideConfig = DevIdeConfig.read(configFile);
    if (ideConfig.mainClassName() == null) {
      // Only a launcher needs it - a test harness brings its own entry point - so it is checked here rather than when read.
      throw new IllegalStateException("'" + DevIdeConfig.MAIN_CLASS_NAME_KEY + "' is missing from " + configFile);
    }

    Map<String, String> properties = readProperties(lookup, classLoader, ideConfig.homePath());
    List<Path> classpath = readClasspath(ideConfig.homePath());

    classLoader.reset(classpath);

    Class<?> mainClass = classLoader.loadClass(ideConfig.mainClassName());

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");
    System.setProperty("idea.home.path", ideConfig.homePath().toAbsolutePath().toString());
    properties.forEach((key, value) -> {
      if (!isCallerOwnedProperty(key) || System.getProperty(key) == null) {
        System.setProperty(key, value);
      }
    });

    //noinspection ConfusingArgumentToVarargsMethod
    lookup.findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).invoke(args);
  }

  private static List<Path> readClasspath(Path ideHomePath) throws IOException {
    List<Path> classpath = new ArrayList<>();
    for (String line : Files.readAllLines(ideHomePath.resolve("core-classpath.txt"))) {
      String cleanedLine = line.trim();
      if (!cleanedLine.isEmpty()) {
        Path path = Path.of(cleanedLine);
        classpath.add(path.isAbsolute() ? path : ideHomePath.resolve(path));
      }
    }
    return classpath;
  }

  private static Map<String, String> readProperties(MethodHandles.Lookup lookup, PathClassLoader classLoader, Path ideHomePath)
    throws Throwable {
    UrlClassLoader.Builder urlClassLoader = UrlClassLoader.build()
      .files(classLoader.getFiles())
      .parent(ClassLoader.getPlatformClassLoader());
    Class<?> buildServer = new PathClassLoader(urlClassLoader).loadClass("org.jetbrains.intellij.build.dev.BuildServerKt");
    MethodHandle getIdeSystemProperties =
      lookup.findStatic(buildServer, "getIdeSystemProperties", MethodType.methodType(Map.class, Path.class));
    //noinspection unchecked
    return (Map<String, String>)getIdeSystemProperties.invoke(ideHomePath);
  }

  /**
   * Properties a launcher passes on the command line win over the distribution's own, for the same keys
   * {@code DevMainImpl.buildDevMain} protects: the product selector and the toolkit name are decisions of whoever starts the
   * IDE, and the toolkit name in particular is rewritten by the JBR on startup and must not be set again afterwards.
   */
  private static boolean isCallerOwnedProperty(String name) {
    return name.regionMatches(true, 0, "rider.", 0, "rider.".length()) ||
           name.regionMatches(true, 0, "resharper.", 0, "resharper.".length()) ||
           name.equals("idea.platform.prefix") ||
           name.equals("idea.suppressed.plugins.set.selector") ||
           name.equals("awt.toolkit.name");
  }

}