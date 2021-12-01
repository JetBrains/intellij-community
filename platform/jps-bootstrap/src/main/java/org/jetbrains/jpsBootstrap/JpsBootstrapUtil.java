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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class JpsBootstrapUtil {
  public static final String TEAMCITY_BUILD_PROPERTIES_FILE_ENV = "TEAMCITY_BUILD_PROPERTIES_FILE";

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
    } else {
      System.err.println("\nFATAL: " + message);
    }

    System.exit(1);
  }

  public static void setVerboseEnabled(boolean verboseEnabled) {
    JpsBootstrapUtil.verboseEnabled = verboseEnabled;
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
}
