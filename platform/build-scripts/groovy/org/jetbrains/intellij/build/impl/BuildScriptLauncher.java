// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.util.ExceptionUtil;
import jetbrains.buildServer.messages.serviceMessages.Message;
import org.jetbrains.intellij.build.dependencies.TeamCityHelper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class BuildScriptLauncher {
  private static final String MAIN_CLASS_PROPERTY = "build.script.launcher.main.class";

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) throws Exception {
    try {
      String mainClassName = System.getProperty(MAIN_CLASS_PROPERTY);
      Class<?> mainClass = BuildScriptLauncher.class.getClassLoader().loadClass(mainClassName);

      //noinspection ConfusingArgumentToVarargsMethod
      MethodHandles.lookup()
        .findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, String[].class))
        .invokeExact(args);

      System.exit(0);
    } catch (Throwable t) {
      String message = ExceptionUtil.getThrowableText(t);

      if (TeamCityHelper.isIsUnderTeamCity()) {
        // Under TeamCity non-zero exit code will be displayed as a separate build error
        // so logging FAILURE message is enough
        System.out.println(new Message(message, "FAILURE", null).asString());
        System.exit(0);
      } else {
        System.err.println("\nFATAL: " + message);
        System.exit(1);
      }
    }
  }
}
