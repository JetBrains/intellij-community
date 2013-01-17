/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Restarter;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {
  private static boolean isHeadless;

  private Main() {
  }

  @SuppressWarnings("MethodNamesDifferingOnlyByCase")
  public static void main(final String[] args) {
    isHeadless = isHeadless(args);
    if (isHeadless) {
      System.setProperty("java.awt.headless", Boolean.TRUE.toString());
    }
    else if (GraphicsEnvironment.isHeadless()) {
      throw new HeadlessException("Unable to detect graphics environment");
    }

    if (!isHeadless) {
      try {
        installPatch();
      }
      catch (IOException e) {
        e.printStackTrace();

        File log = null;
        try {
          log = FileUtil.createTempFile("patch", ".log", false);
          PrintWriter writer = new PrintWriter(log);
          try {
            e.printStackTrace(writer);
          }
          finally {
            writer.close();
          }
        }
        catch (IOException ignore) {
          ignore.printStackTrace();
        }

        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Throwable ignore) {
        }
        String message = e.getMessage()
                         + "\n" + (log == null ? "Log cannot be saved" : "Log is saved in " + log)
                         + "\n\nPlease download and install update manually" ;
        JOptionPane.showMessageDialog(null, message, "Cannot Apply Patch", JOptionPane.ERROR_MESSAGE);
      }
    }

    Bootstrap.main(args, Main.class.getName() + "Impl", "start");
  }

  public static boolean isHeadless(final String[] args) {
    final Boolean forceEnabledHeadlessMode = Boolean.valueOf(System.getProperty("java.awt.headless"));

    @NonNls final String antAppCode = "ant";
    @NonNls final String duplocateCode = "duplocate";
    @NonNls final String traverseUI = "traverseUI";
    if (args.length == 0) {
      return false;
    }
    final String firstArg = args[0];
    return forceEnabledHeadlessMode ||
           Comparing.strEqual(firstArg, antAppCode) ||
           Comparing.strEqual(firstArg, duplocateCode) ||
           Comparing.strEqual(firstArg, traverseUI) ||
           (firstArg.length() < 20 && firstArg.endsWith("inspect"));
  }

  public static boolean isUITraverser(final String[] args) {
    return args.length > 0 && Comparing.strEqual(args[0], "traverseUI");
  }

  public static boolean isCommandLine(final String[] args) {
    if (isHeadless(args)) return true;
    @NonNls final String diffAppCode = "diff";
    return args.length > 0 && Comparing.strEqual(args[0], diffAppCode);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  private static void installPatch() throws IOException {
    String platform = System.getProperty("idea.platform.prefix", "idea");
    String patchFileName = ("jetbrains.patch.jar." + platform).toLowerCase();
    File originalPatchFile = new File(System.getProperty("java.io.tmpdir"), patchFileName);
    File copyPatchFile = new File(System.getProperty("java.io.tmpdir"), patchFileName + "_copy");

    // always delete previous patch copy
    if (!FileUtil.delete(copyPatchFile)) throw new IOException("Cannot create temporary patch file");

    if (!originalPatchFile.exists()) return;

    if (!originalPatchFile.renameTo(copyPatchFile) || !FileUtil.delete(originalPatchFile)) {
      throw new IOException("Cannot create temporary patch file");
    }

    List<String> args = new ArrayList<String>();
    if (SystemInfo.isWindows) {
      args.add(Restarter.createTempExecutable(new File(PathManager.getBinPath(), "vistalauncher.exe")).getPath());
    }

    Collections.addAll(args,
                       System.getProperty("java.home") + "/bin/java",
                       "-classpath",
                       copyPatchFile.getPath(),
                       "com.intellij.updater.Runner",
                       "install",
                       PathManager.getHomePath());

    System.exit(Restarter.scheduleRestart(args.toArray(new String[args.size()])));
  }
}
