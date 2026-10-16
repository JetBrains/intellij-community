// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap.dev;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge for a JVM whose class path is a module, not the IDE: the docker run configuration {@code IDEA_Backend_in_Docker}.
 * <p>
 * An "Application" run configuration always starts the JVM with a module class path. So this class does in the JVM what a command line
 * does before the JVM starts. It resets the class loader to {@code core-classpath.txt}, sets the distribution properties and starts the
 * main class. A command line {@code java @<argfile>} starts every other dev launch.
 * <p>
 * Reads configuration from a file specified by the "idea.ide.config.path" system property. The value is either a path or,
 * under Bazel, a runfiles-relative one - see {@link DevIdeConfig#resolveConfigFile}.
 * The home is the directory that the configuration names, such as the dev build that {@code BuildBackendForDocker} writes.
 * <p>
 * With {@code -Didea.dev.mode.custom.command=true} the first program argument names a custom command of the distribution,
 * see {@link CustomCommandLaunch}. A plain launch reads no command.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
@ApiStatus.Internal
public final class PreBuiltDevMain {
  private static final String RUNTIME_MODULE_REPOSITORY_PROPERTY = "intellij.platform.runtime.repository.path";

  static void main(String[] args) throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    if (!(PreBuiltDevMain.class.getClassLoader() instanceof PathClassLoader classLoader)) {
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

    Path homePath = ideConfig.homePath();
    Class<?> buildServer = loadBuildServer(classLoader);
    Map<String, String> properties = readProperties(lookup, buildServer, homePath);
    String mainClassName = ideConfig.mainClassName();
    if (CustomCommandLaunch.isRequested()) {
      Map.Entry<String, Map<String, String>> command = CustomCommandLaunch.read(lookup, buildServer, homePath, args);
      mainClassName = command.getKey();
      properties = new LinkedHashMap<>(properties);
      properties.putAll(command.getValue());
    }
    properties = addRuntimeModuleRepository(properties, homePath);
    List<Path> classpath = readClasspath(homePath);

    classLoader.reset(classpath);

    Class<?> mainClass = classLoader.loadClass(mainClassName);

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");
    System.setProperty("idea.home.path", homePath.toAbsolutePath().toString());
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

  /**
   * The launch property readers of {@code DevLaunchProperties.kt} in a class loader of their own, so that nothing of them stays
   * loaded in the class loader the IDE then runs in.
   */
  private static Class<?> loadBuildServer(PathClassLoader classLoader) throws ClassNotFoundException {
    UrlClassLoader.Builder urlClassLoader = UrlClassLoader.build()
      .files(classLoader.getFiles())
      .parent(ClassLoader.getPlatformClassLoader());
    return new PathClassLoader(urlClassLoader).loadClass("com.intellij.platform.buildScripts.devLaunch.DevLaunchPropertiesKt");
  }

  private static Map<String, String> readProperties(MethodHandles.Lookup lookup, Class<?> buildServer, Path ideHomePath) throws Throwable {
    MethodHandle getIdeSystemProperties =
      lookup.findStatic(buildServer, "getIdeSystemProperties", MethodType.methodType(Map.class, Path.class));
    //noinspection unchecked
    return (Map<String, String>)getIdeSystemProperties.invoke(ideHomePath);
  }

  /**
   * Adds the runtime module repository of the home, as {@code add_runtime_module_repository} of the launcher does.
   * <p>
   * The distribution states the property through {@code product-info.json} when its launch model asks. A row that composes the
   * repository component gets it from the home. So the method adds {@code <home>/modules/module-descriptors.dat} only when the home
   * has that file and neither the distribution nor the command line states the property.
   */
  private static Map<String, String> addRuntimeModuleRepository(Map<String, String> properties, Path homePath) {
    if (properties.containsKey(RUNTIME_MODULE_REPOSITORY_PROPERTY) || System.getProperty(RUNTIME_MODULE_REPOSITORY_PROPERTY) != null) {
      return properties;
    }
    var repository = homePath.resolve("modules").resolve("module-descriptors.dat");
    if (!Files.isRegularFile(repository)) {
      return properties;
    }
    var result = new LinkedHashMap<>(properties);
    result.put(RUNTIME_MODULE_REPOSITORY_PROPERTY, repository.toAbsolutePath().toString());
    return result;
  }

  /**
   * Properties a launcher passes on the command line win over the distribution's own, for the same keys
   * {@code DevMainImpl.buildDevMain} protects: the product selector, the product mode and the toolkit name are decisions of
   * whoever starts the IDE, and the toolkit name in particular is rewritten by the JBR on startup and must not be set again afterwards.
   */
  private static boolean isCallerOwnedProperty(String name) {
    return name.regionMatches(true, 0, "rider.", 0, "rider.".length()) ||
           name.regionMatches(true, 0, "resharper.", 0, "resharper.".length()) ||
           name.equals("idea.platform.prefix") ||
           name.equals("intellij.platform.product.mode") ||
           name.equals("idea.suppressed.plugins.set.selector") ||
           name.equals("awt.toolkit.name");
  }
}
