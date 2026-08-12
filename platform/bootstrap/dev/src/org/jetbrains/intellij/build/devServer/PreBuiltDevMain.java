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
 * Reads configuration from a file specified by the "idea.ide.config.path" system property. The value is either a path or,
 * under Bazel, a runfiles-relative one - see {@link #resolveIdeConfigPath}.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
@ApiStatus.Internal
public final class PreBuiltDevMain {
  private static final String IDE_CONFIG_PATH_PROPERTY = "idea.ide.config.path";

  public static void main(String[] args) throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    if (!(DevMainKt.class.getClassLoader() instanceof PathClassLoader classLoader)) {
      System.err.println("The current class loader is not a com.intellij.util.lang.PathClassLoader.");
      return;
    }

    IdeConfig ideConfig = readIdeConfig(resolveIdeConfigPath(System.getProperty(IDE_CONFIG_PATH_PROPERTY)));

    Map<String, String> properties = readProperties(lookup, classLoader, ideConfig.homePath);
    List<Path> classpath = readClasspath(ideConfig.homePath);

    classLoader.reset(classpath);

    Class<?> mainClass = classLoader.loadClass(ideConfig.mainClassName);

    System.setProperty("idea.vendor.name", "JetBrains");
    System.setProperty("idea.use.dev.build.server", "true");
    System.setProperty("idea.home.path", ideConfig.homePath.toAbsolutePath().toString());
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

  /**
   * A Bazel launcher cannot know where its runfiles will be, so it names the config file by its runfiles-relative path
   * (`$(rlocationpath ...)`) and the value is resolved here - the same contract as
   * {@code intellij.build.bazel.targets.json.file}. Resolution is hand-rolled rather than delegated to
   * {@code BazelRunfiles} because this class must not pull Kotlin into the boot classloader.
   * <p>
   * An absolute path, or one that resolves against the working directory, is taken verbatim: that is the containerized
   * case this launcher was written for, where there is no runfiles tree at all.
   */
  private static Path resolveIdeConfigPath(String value) throws IOException {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("System property '" + IDE_CONFIG_PATH_PROPERTY + "' is not set");
    }

    Path asGiven = Path.of(value);
    if (asGiven.isAbsolute() || Files.exists(asGiven)) {
      return asGiven;
    }

    for (String variable : new String[]{"JAVA_RUNFILES", "RUNFILES_DIR"}) {
      String runfilesDir = System.getenv(variable);
      if (runfilesDir != null && !runfilesDir.isEmpty()) {
        Path candidate = Path.of(runfilesDir).resolve(value);
        if (Files.exists(candidate)) {
          return candidate;
        }
      }
    }

    // Windows has no runfiles tree, only this manifest of `<runfiles-relative path> <absolute path>` lines.
    String manifest = System.getenv("RUNFILES_MANIFEST_FILE");
    if (manifest != null && !manifest.isEmpty()) {
      for (String line : Files.readAllLines(Path.of(manifest))) {
        int separator = line.indexOf(' ');
        if (separator == value.length() && line.startsWith(value)) {
          return Path.of(line.substring(separator + 1));
        }
      }
    }

    throw new IllegalStateException(
      IDE_CONFIG_PATH_PROPERTY + "='" + value + "' names neither an existing file nor a runfile" +
      " (JAVA_RUNFILES=" + System.getenv("JAVA_RUNFILES") +
      ", RUNFILES_DIR=" + System.getenv("RUNFILES_DIR") +
      ", RUNFILES_MANIFEST_FILE=" + manifest + ")");
  }

  private static IdeConfig readIdeConfig(Path path) throws Exception {
    Properties properties = new Properties();
    try (var stream = Files.newInputStream(path)) {
      properties.load(stream);
    }
    // A relative home is resolved against the config file, so that a config written next to a distribution keeps naming it
    // after both have been moved - a build artifact is read from a different path than it was written to. Same rule as
    // the classpath entries above.
    Path homePath = Path.of(properties.getProperty("home.path"));
    if (!homePath.isAbsolute()) {
      homePath = path.toAbsolutePath().getParent().resolve(homePath);
    }
    return new IdeConfig(homePath, properties.getProperty("main.class.name"));
  }

  record IdeConfig(Path homePath, String mainClassName) {
  }
}