// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import jetbrains.buildServer.messages.serviceMessages.Message;
import org.jetbrains.annotations.Contract;

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
      System.out.println(new Message(message, "INFO", null).asString());
    } else {
      System.out.println(message);
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
