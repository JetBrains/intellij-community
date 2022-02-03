// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import jetbrains.buildServer.messages.serviceMessages.Message;
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.Contract;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class JpsBootstrapUtil {
  public static final String TEAMCITY_BUILD_PROPERTIES_FILE_ENV = "TEAMCITY_BUILD_PROPERTIES_FILE";
  public static final String TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY = "teamcity.configuration.properties.file";

  public static final boolean underTeamCity = System.getenv("TEAMCITY_VERSION") != null;

  private static boolean verboseEnabled = false;

  public static void warn(String message) {
    if (underTeamCity) {
      System.out.println(new Message(message, "WARNING", null).asString());
    } else {
      System.out.println(message);
    }
  }

  public static void info(String message) {
    if (underTeamCity) {
      System.out.println(new Message(message, "NORMAL", null).asString());
    } else {
      System.out.println(message);
    }
  }

  public static void verbose(String message) {
    if (underTeamCity) {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("text", message);
      attributes.put("status", "NORMAL");
      attributes.put("tc:tags", "tc:internal");
      System.out.println(new MessageWithAttributes(ServiceMessageTypes.MESSAGE, attributes) {}.asString());
    } else {
      if (verboseEnabled) {
        System.out.println(message);
      }
    }
  }

  public static void error(String message) {
    if (underTeamCity) {
      System.out.println(new Message(message, "ERROR", null).asString());
    } else {
      System.out.println("ERROR: " + message);
    }
  }

  @Contract("_->fail")
  public static void fatal(String message) {
    if (underTeamCity) {
      System.out.println(new Message(message, "FAILURE", null).asString());
      // Under TeamCity non-zero exit code will be displayed as a separate build error
      // so logging FAILURE message is enough
      System.exit(0);
    } else {
      System.err.println("\nFATAL: " + message);
      System.exit(1);
    }
  }

  public static void setVerboseEnabled(boolean verboseEnabled) {
    JpsBootstrapUtil.verboseEnabled = verboseEnabled;
  }

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
      JpsBootstrapUtil.info("Finished all tasks in " + (System.currentTimeMillis() - start) + " ms");

      if (!executorService.isShutdown()) {
        executorService.shutdownNow();
      }
    }
  }
}
