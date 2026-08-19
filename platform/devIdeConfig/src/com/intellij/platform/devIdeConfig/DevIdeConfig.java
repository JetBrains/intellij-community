// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.devIdeConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * The handshake between a process that assembles a dev IDE distribution and a process that starts one.
 * <p>
 * The assembler ({@code DevDistMain}) writes this file next to the distribution; a launcher
 * ({@code PreBuiltDevMain}) or a test harness reads it to find the distribution, the class to start, and what the
 * distribution actually is - which product, and which plugin modules were built into it. A consumer that needs a
 * different module set is looking at the wrong distribution, and can only notice because the distribution says so
 * here.
 * <p>
 * This class carries the whole format - keys, the relative-home rule, and the runfiles lookup - because its three
 * users sit in three different dependency layers and would otherwise each own a copy. It therefore depends on
 * nothing: not merely to stay small, but because {@code PreBuiltDevMain} must not pull Kotlin into the boot
 * classloader. Keep it plain Java with no module dependencies.
 */
public final class DevIdeConfig {
  /**
   * Names the config file, and thereby the distribution to use: an absolute path, or a runfiles-relative one - see
   * {@link #resolveConfigFile}. Unset means "no distribution was prepared", which is what tells a test harness to
   * assemble one itself.
   */
  public static final String CONFIG_PATH_PROPERTY = "idea.ide.config.path";
  /** The distribution's home directory. Relative values are resolved against the config file's own directory. */
  public static final String HOME_PATH_KEY = "home.path";
  /** The IDE entry point the launcher invokes once it has reset the classloader to the distribution's classpath. */
  public static final String MAIN_CLASS_NAME_KEY = "main.class.name";
  /** The product the distribution was assembled as, as {@code -Didea.platform.prefix} names it. */
  public static final String PLATFORM_PREFIX_KEY = "platform.prefix";
  /** The plugin modules built in on top of the product's own, comma-separated, as {@code -Dadditional.modules}. */
  public static final String ADDITIONAL_MODULES_KEY = "additional.modules";

  private DevIdeConfig() {
  }

  /**
   * @param homePath          the distribution, already resolved to an absolute path
   * @param platformPrefix    {@code null} for a config file written before this key existed, or by hand
   * @param additionalModules empty when the distribution declares none - which is not the same as "unknown", and is
   *                          why a consumer asking for modules must fail against it rather than assume
   */
  public record Content(Path homePath, String mainClassName, String platformPrefix, List<String> additionalModules) {
  }

  /**
   * Writes the config file for a distribution at [home].
   * <p>
   * The home is named relatively whenever the config file sits above it, so that the pair can be moved as a unit - a
   * build artifact is read from a different path than it was written to. Separators are made invariant for the same
   * reason they are in {@code core-classpath.txt}: a {@link Properties} file treats a Windows backslash as an escape.
   */
  public static void write(
    Path configFile,
    Path home,
    String mainClassName,
    String platformPrefix,
    Collection<String> additionalModules
  ) throws IOException {
    // A `null` here would be written as the four characters "null" and read back as a product named that, which is a
    // worse outcome than refusing to write the file.
    Objects.requireNonNull(mainClassName, MAIN_CLASS_NAME_KEY);
    Objects.requireNonNull(platformPrefix, PLATFORM_PREFIX_KEY);

    Path configDir = configFile.toAbsolutePath().getParent();
    Path absoluteHome = home.toAbsolutePath();
    Path homePath = configDir != null && absoluteHome.startsWith(configDir) ? configDir.relativize(absoluteHome) : absoluteHome;

    if (configDir != null) {
      Files.createDirectories(configDir);
    }
    Files.writeString(configFile, HOME_PATH_KEY + '=' + invariantSeparators(homePath) + '\n' +
                                  MAIN_CLASS_NAME_KEY + '=' + mainClassName + '\n' +
                                  PLATFORM_PREFIX_KEY + '=' + platformPrefix + '\n' +
                                  ADDITIONAL_MODULES_KEY + '=' + String.join(",", additionalModules) + '\n');
  }

  public static Content read(Path configFile) throws IOException {
    Properties properties = new Properties();
    try (var stream = Files.newInputStream(configFile)) {
      properties.load(stream);
    }

    String home = properties.getProperty(HOME_PATH_KEY);
    if (home == null || home.isBlank()) {
      throw new IllegalStateException("'" + HOME_PATH_KEY + "' is missing from " + configFile);
    }
    Path homePath = Path.of(home);
    if (!homePath.isAbsolute()) {
      homePath = configFile.toAbsolutePath().getParent().resolve(homePath);
    }

    return new Content(homePath.normalize(), properties.getProperty(MAIN_CLASS_NAME_KEY), properties.getProperty(PLATFORM_PREFIX_KEY),
                       splitModules(properties.getProperty(ADDITIONAL_MODULES_KEY)));
  }

  /**
   * The config file named by {@link #CONFIG_PATH_PROPERTY}, or {@code null} when the property is unset.
   * <p>
   * A property that is set but names nothing is an error, not an absence: it is a declaration that did not survive,
   * and silently assembling instead would hide it.
   */
  public static Path declaredConfigFile() throws IOException {
    String value = System.getProperty(CONFIG_PATH_PROPERTY);
    if (value == null || value.isBlank()) {
      return null;
    }
    return resolveConfigFile(value);
  }

  /**
   * Resolves the value a caller was given for the config file.
   * <p>
   * A Bazel target cannot know where its runfiles will be, so it names the file by its runfiles-relative path
   * ({@code $(rlocationpath ...)}) and the value is resolved here - the same contract as
   * {@code intellij.build.bazel.targets.json.file}. An absolute path, or one that resolves against the working
   * directory, is taken verbatim: that is the containerized case, where there is no runfiles tree at all.
   * <p>
   * Hand-rolled rather than delegated to {@code BazelRunfiles} for the no-dependencies reason above.
   */
  public static Path resolveConfigFile(String value) throws IOException {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("No dev IDE distribution config file was named");
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
      "'" + value + "' names neither an existing file nor a runfile" +
      " (JAVA_RUNFILES=" + System.getenv("JAVA_RUNFILES") +
      ", RUNFILES_DIR=" + System.getenv("RUNFILES_DIR") +
      ", RUNFILES_MANIFEST_FILE=" + manifest + ")");
  }

  private static List<String> splitModules(String value) {
    List<String> modules = new ArrayList<>();
    if (value != null) {
      for (String module : value.split(",")) {
        String trimmed = module.trim();
        if (!trimmed.isEmpty()) {
          modules.add(trimmed);
        }
      }
    }
    return List.copyOf(modules);
  }

  private static String invariantSeparators(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }
}
