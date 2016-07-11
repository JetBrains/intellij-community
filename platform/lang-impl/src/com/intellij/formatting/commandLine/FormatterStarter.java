/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting.commandLine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * A launcher class for command-line formatter.
 */
public class FormatterStarter extends ApplicationStarterEx {

  public static final String FORMAT_COMMAND_NAME = "format";

  @Override
  public boolean isHeadless() {
    return true;
  }

  @Override
  public String getCommandName() {
    return FORMAT_COMMAND_NAME;
  }

  @Override
  public void premain(String[] args) {
  }

  @Override
  public void main(String[] args) {
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    MessageOutput messageOutput = new MessageOutput(
      new PrintWriter(System.out),
      new PrintWriter(System.err));
    messageOutput.info(getAppInfo() + " Formatter\n");
    for (int i = 1; i < args.length; i ++) {
      if (args[i].startsWith("-")) {
        if (checkOption(args[i], "-h", "--help")) {
          showUsageInfo(messageOutput);
        }
        else {
          messageOutput.error("Unknown option " + args[i]);
        }
      }
      else {
        FileSetFormatter fileSetFormatter = new FileSetFormatter(args[i], messageOutput);
        try {
          fileSetFormatter.processFiles();
        }
        catch (IOException e) {
          messageOutput.error("FAILED: " + e.getLocalizedMessage() + "\n");
          System.exit(1);
        }
      }
    }
    ((ApplicationEx)ApplicationManager.getApplication()).exit(true, true);
  }

  private static boolean checkOption(@NotNull String arg, String... variants) {
    for (String variant: variants) {
      if (variant.equals(arg)) return true;
    }
    return false;
  }

  private static String getAppInfo() {
    ApplicationInfoImpl appInfo = (ApplicationInfoImpl)ApplicationInfoEx.getInstanceEx();
    return String.format("%s, build %s", appInfo.getFullApplicationName(), appInfo.getBuild().asString());
  }


  private static void showUsageInfo(@NotNull MessageOutput messageOutput) {
    messageOutput.info("Usage: format fileSpec...");
  }
}
