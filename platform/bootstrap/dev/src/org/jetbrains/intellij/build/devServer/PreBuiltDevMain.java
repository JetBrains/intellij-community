// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer;

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
import java.util.Properties;

/**
 * Launcher for IDE pre-built from sources.
 * <p>
 * Separates IDE runtime phase from the build phase, which is useful for containerized
 * environments where the build happens on the host system and the IDE runs inside a container.
 * <p>
 * Reads configuration from a file specified by the "idea.ide.config.path" system property.
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

    IdeConfig ideConfig = readIdeConfig(Path.of(System.getProperty("idea.ide.config.path")));

    Map<String, String> properties = readProperties(lookup, classLoader, ideConfig.homePath);
    List<Path> classpath = readClasspath(ideConfig.homePath);

    classLoader.reset(classpath);

    Class<?> mainClass = classLoader.loadClass(ideConfig.mainClassName);

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");
    System.setProperty("idea.home.path", ideConfig.homePath.toAbsolutePath().toString());
    properties.forEach((key, value) -> System.setProperty(key, value));

    //noinspection ConfusingArgumentToVarargsMethod
    lookup.findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).invoke(args);
  }

  private static List<Path> readClasspath(Path ideHomePath) throws IOException {
    List<Path> classpath = new ArrayList<>();
    for (String line : Files.readAllLines(ideHomePath.resolve("core-classpath.txt"))) {
      String cleanedLine = line.trim();
      if (!cleanedLine.isEmpty()) {
        classpath.add(Path.of(cleanedLine));
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

  private static IdeConfig readIdeConfig(Path path) throws Exception {
    Properties properties = new Properties();
    try (var stream = Files.newInputStream(path)) {
      properties.load(stream);
    }
    return new IdeConfig(
      Path.of(properties.getProperty("home.path")),
      properties.getProperty("main.class.name")
    );
  }

  record IdeConfig(Path homePath, String mainClassName) {
  }
}