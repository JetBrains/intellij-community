package org.jetbrains.intellij.build.dependencies;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unused")
@ApiStatus.Internal
public class TeamCityHelper {
  public static final boolean isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null;

  @Nullable
  public static Path getCheckoutDirectory() {
    if (!isUnderTeamCity) {
      return null;
    }

    String name = "teamcity.build.checkoutDir";

    String value = getSystemProperties().get(name);
    if (value == null || value.isEmpty()) {
      throw new RuntimeException("TeamCity system property " + name + "was not found while running under TeamCity");
    }

    Path file = Path.of(value);
    if (!Files.isDirectory(file)) {
      throw new RuntimeException("TeamCity system property " + name + " contains non existent directory: " + file);
    }

    return file;
  }

  @NotNull
  public static Map<String, String> getSystemProperties() {
    return systemPropertiesValue.get();
  }

  @NotNull
  public static Map<String, String> getAllProperties() {
    return allPropertiesValue.get();
  }

  @Nullable
  public static Path getTempDirectory() {
    Map<String, String> systemProperties = getSystemProperties();
    if (systemProperties.isEmpty()) {
      return null;
    }

    String propertyName = "teamcity.build.tempDir";

    String tempPath = systemProperties.get(propertyName);
    if (tempPath == null) {
      throw new IllegalStateException("TeamCity must provide system property " + propertyName);
    }

    return Path.of(tempPath);
  }

  private static final Supplier<Map<String, String>> systemPropertiesValue = Suppliers.memoize(() -> {
    if (!isUnderTeamCity) {
      return new HashMap<>();
    }

    String systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE";

    String systemPropertiesFile = System.getenv(systemPropertiesEnvName);
    if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
      throw new RuntimeException("TeamCity environment variable " + systemPropertiesEnvName + "was not found while running under TeamCity");
    }

    Path file = Path.of(systemPropertiesFile);
    if (!Files.exists(file)) {
      throw new RuntimeException("TeamCity system properties file is not found: " + file);
    }

    return BuildDependenciesUtil.loadPropertiesFile(file);
  });

  private static final Supplier<Map<String, String>> allPropertiesValue = Suppliers.memoize(() -> {
    if (!isUnderTeamCity) {
      return new HashMap<>();
    }

    String propertyName = "teamcity.configuration.properties.file";

    String value = getSystemProperties().get(propertyName);
    if (value == null || value.isEmpty()) {
      throw new RuntimeException("TeamCity system property '" + propertyName + " is not found");
    }

    Path file = Path.of(value);
    if (!Files.exists(file)) {
      throw new RuntimeException("TeamCity configuration properties file was not found: " + file);
    }

    return BuildDependenciesUtil.loadPropertiesFile(file);
  });
}
