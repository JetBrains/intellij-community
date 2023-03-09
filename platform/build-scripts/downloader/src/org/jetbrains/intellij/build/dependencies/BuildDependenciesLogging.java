// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import jetbrains.buildServer.messages.serviceMessages.Message;
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.underTeamCity;

public final class BuildDependenciesLogging {
  public static void setVerboseEnabled(boolean verboseEnabled) {
    BuildDependenciesLogging.verboseEnabled = verboseEnabled;
  }

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

  public static void fatal(String message) {
    if (underTeamCity) {
      System.out.println(new Message(message, "FAILURE", null).asString());
    } else {
      System.err.println("\nFATAL: " + message);
    }
  }
}
