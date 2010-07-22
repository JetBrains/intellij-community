/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {
  private static boolean isHeadless;

  private Main() {
  }

  public static void main(final String[] args) {
    if (installPatch()) {
      JOptionPane.showMessageDialog(null, "The application cannot start right away since some critical files have been changed, " +
                                          "please restart it manually.");
      return;
    }

    isHeadless = isHeadless(args);
    if (isHeadless) {
      System.setProperty("java.awt.headless", Boolean.TRUE.toString());
    }
    else if (GraphicsEnvironment.isHeadless()) {
      throw new HeadlessException("Unable to detect graphics environment");
    }

    Bootstrap.main(args, Main.class.getName() + "Impl", "start");
  }

  public static boolean isHeadless(final String[] args) {
    @NonNls final String inspectAppCode = "inspect";
    @NonNls final String antAppCode = "ant";
    @NonNls final String duplocateCode = "duplocate";
    @NonNls final String traverseUI = "traverseUI";
    return args.length > 0 && (Comparing.strEqual(args[0], inspectAppCode) ||
                               Comparing.strEqual(args[0], antAppCode) ||
                               Comparing.strEqual(args[0], duplocateCode) ||
                               Comparing.strEqual(args[0], traverseUI));
  }

  public static boolean isCommandLine(final String[] args) {
    if (isHeadless(args)) return true;
    @NonNls final String diffAppCode = "diff";
    return args.length > 0 && Comparing.strEqual(args[0], diffAppCode);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  private static boolean installPatch() {
    try {
      File ideaHomeDir = getIdeaHomeDir();
      if (ideaHomeDir == null) return false;

      String platform = System.getProperty("idea.platform.prefix", "idea");
      String patchFileName = ("jetbrains.patch.jar." + platform).toLowerCase();
      File patchFile = new File(System.getProperty("java.io.tmpdir"), patchFileName);

      if (!patchFile.exists()) return false;

      try {
        List<String> args = new ArrayList<String>();
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
          File launcherFile = new File(ideaHomeDir, "bin/vistalauncher.exe");
          File launcherCopy = File.createTempFile("vistalauncher", ".exe");
          launcherCopy.deleteOnExit();
          copyFile(launcherFile, launcherCopy);
          args.add(launcherCopy.getPath());
        }

        Collections.addAll(args,
                           System.getProperty("java.home") + "/bin/java",
                           "-classpath",
                           patchFile.getPath(),
                           "com.intellij.updater.Runner",
                           "install",
                           ideaHomeDir.getPath());
        Process process = Runtime.getRuntime().exec(args.toArray(new String[args.size()]));

        Thread outThread = new Thread(new StreamRedirector(process.getInputStream(), System.out));
        Thread errThread = new Thread(new StreamRedirector(process.getErrorStream(), System.err));
        outThread.start();
        errThread.start();

        try {
          boolean requiresRestart = process.waitFor() == 42;
          return requiresRestart;
        }
        finally {
          outThread.join();
          errThread.join();
        }
      }
      finally {
        patchFile.delete();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  private static class StreamRedirector implements Runnable {
    private final InputStream myIn;
    private final OutputStream myOut;

    private StreamRedirector(InputStream in, OutputStream out) {
      myIn = in;
      myOut = out;
    }

    public void run() {
      try {
        copyStream(myIn, myOut);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static File getIdeaHomeDir() throws IOException {
    URL url = Bootstrap.class.getResource("");
    if (url == null || !"jar".equals(url.getProtocol())) return null;

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) return null;

    String jarFileUrl = path.substring(start, end);

    try {
      File bootstrapJar = new File(new URI(jarFileUrl));
      return bootstrapJar.getParentFile().getParentFile();
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  public static void copyFile(File from, File to) throws IOException {
    to.getParentFile().mkdirs();

    FileInputStream is = null;
    FileOutputStream os = null;
    try {
      is = new FileInputStream(from);
      os = new FileOutputStream(to);

      copyStream(is, os);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (os != null) {
        try {
          os.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void copyStream(InputStream from, OutputStream to) throws IOException {
    byte[] buffer = new byte[65536];
    while (true) {
      int read = from.read(buffer);
      if (read < 0) break;
      to.write(buffer, 0, read);
    }
  }
}
