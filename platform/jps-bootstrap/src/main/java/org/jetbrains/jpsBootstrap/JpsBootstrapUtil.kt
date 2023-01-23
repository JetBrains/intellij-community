// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info;
import static org.jetbrains.jpsBootstrap.JpsBootstrapMain.underTeamCity;

public final class JpsBootstrapUtil {
  public static final String TEAMCITY_BUILD_PROPERTIES_FILE_ENV = "TEAMCITY_BUILD_PROPERTIES_FILE";
  public static final String TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY = "teamcity.configuration.properties.file";

  public static final String JPS_RESOLUTION_RETRY_ENABLED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.enabled";
  public static final String JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.max.attempts";
  public static final String JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.delay.ms";
  public static final String JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.backoff.limit.ms";


  public static boolean toBooleanChecked(String s) {
    switch (s) {
      case "true": return true;
      case "false": return false;
      default:
        throw new IllegalArgumentException("Could not convert '" + s + "' to boolean. Only 'true' or 'false' values are accepted");
    }
  }

  public static Properties getTeamCitySystemProperties() throws IOException {
    if (!underTeamCity) {
      throw new IllegalStateException("Not under TeamCity");
    }

    final String buildPropertiesFile = System.getenv(TEAMCITY_BUILD_PROPERTIES_FILE_ENV);
    if (buildPropertiesFile == null || buildPropertiesFile.length() == 0) {
      throw new IllegalStateException("'TEAMCITY_BUILD_PROPERTIES_FILE_ENV' env. variable is missing or empty under TeamCity build");
    }

    Properties properties = new Properties();
    try (BufferedReader reader = Files.newBufferedReader(Path.of(buildPropertiesFile))) {
      properties.load(reader);
    }

    return properties;
  }

  public static Properties getTeamCityConfigProperties() throws IOException {
    Properties systemProperties = getTeamCitySystemProperties();

    final String configPropertiesFile = systemProperties.getProperty(TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY);
    if (configPropertiesFile == null || configPropertiesFile.length() == 0) {
      throw new IllegalStateException("TeamCity system property '" + TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY + "' is missing under TeamCity build");
    }

    Properties properties = new Properties();
    try (BufferedReader reader = Files.newBufferedReader(Path.of(configPropertiesFile))) {
      properties.load(reader);
    }

    return properties;
  }

  public static String getTeamCityConfigPropertyOrThrow(String configProperty) throws IOException {
    final Properties properties = getTeamCityConfigProperties();
    final String value = properties.getProperty(configProperty);
    if (value == null) {
      throw new IllegalStateException("TeamCity config property " + configProperty + " was not found");
    }
    return value;
  }

  /**
   * Create properties to enable artifacts resolution retries in org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
   * if ones absent in {@code existingProperties}. Latest of {@code existingProperties} has the highest priority.
   *
   * @param existingProperties Existing properties to check whether required values already present.
   * @return Properties to enable artifacts resolution retries while build.
   */
  public static Properties getJpsArtifactsResolutionRetryProperties(final Properties... existingProperties) {
    final Properties properties = new Properties();

    final Properties existingPropertiesMerged = new Properties();
    for (Properties it : existingProperties) {
      existingPropertiesMerged.putAll(it);
    }

    String enabled = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_ENABLED_PROPERTY, "true");
    properties.put(JPS_RESOLUTION_RETRY_ENABLED_PROPERTY, enabled);

    String maxAttempts = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, "3");
    properties.put(JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, maxAttempts);

    String initialDelayMs = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY, "1000");
    properties.put(JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY, initialDelayMs);

    String backoffLimitMs = existingPropertiesMerged.getProperty(
      JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY,
      Long.toString(TimeUnit.MINUTES.toMillis(5))
    );
    properties.put(JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY, backoffLimitMs);

    return properties;
  }

  static <T> List<T> executeTasksInParallel(List<Callable<T>> tasks) throws InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(5);
    long start = System.currentTimeMillis();

    try {
      info("Executing " + tasks.size() + " in parallel");

      List<Future<T>> futures = executorService.invokeAll(tasks);

      List<Throwable> errors = new ArrayList<>();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        try {
          T r = future.get(10, TimeUnit.MINUTES);
          results.add(r);
        }
        catch (ExecutionException e) {
          errors.add(e.getCause());
          if (errors.size() > 4) {
            executorService.shutdownNow();
            break;
          }
        }
        catch (TimeoutException e) {
          throw new IllegalStateException("Timeout waiting for results, exiting");
        }
      }

      if (errors.size() > 0) {
        RuntimeException t = new RuntimeException("Unable to execute all targets, " + errors.size() + " error(s)");
        for (Throwable err : errors) {
          t.addSuppressed(err);
        }
        throw t;
      }

      if (results.size() != tasks.size()) {
        throw new IllegalStateException("received results size != tasks size (" + results.size() + " != " + tasks.size() + ")");
      }

      return results;
    } finally {
      info("Finished all tasks in " + (System.currentTimeMillis() - start) + " ms");

      if (!executorService.isShutdown()) {
        executorService.shutdownNow();
      }
    }
  }
}
