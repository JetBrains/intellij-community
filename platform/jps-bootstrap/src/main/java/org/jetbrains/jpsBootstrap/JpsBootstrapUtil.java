// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import jetbrains.buildServer.messages.serviceMessages.Message;
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.Contract;

import java.util.HashMap;
import java.util.Map;

public class JpsBootstrapUtil {
  public static final boolean underTeamCity = System.getenv("TEAMCITY_VERSION") != null;

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
      System.out.println(message);
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
      System.err.println("FATAL: " + message);
    }

    System.exit(1);
  }
}
