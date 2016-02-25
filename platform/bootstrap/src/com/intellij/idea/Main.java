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
package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Restarter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static java.io.File.pathSeparator;

public class Main {
  public static final int NO_GRAPHICS = 1;
  public static final int UPDATE_FAILED = 2;
  public static final int STARTUP_EXCEPTION = 3;
  public static final int JDK_CHECK_FAILED = 4;
  public static final int DIR_CHECK_FAILED = 5;
  public static final int INSTANCE_CHECK_FAILED = 6;
  public static final int LICENSE_ERROR = 7;
  public static final int PLUGIN_ERROR = 8;
  public static final int OUT_OF_MEMORY = 9;
  public static final int UNSUPPORTED_JAVA_VERSION = 10;

  private static final String AWT_HEADLESS = "java.awt.headless";
  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final String[] NO_ARGS = {};

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean hasGraphics = true;

  private Main() { }

  @SuppressWarnings("MethodNamesDifferingOnlyByCase")
  public static void main(String[] args) {
    if (args.length == 1 && "%f".equals(args[0])) {
      args = NO_ARGS;
    }

    if (args.length == 1 && args[0].startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(args[0]);
      args = NO_ARGS;
    }

    setFlags(args);

    if (isHeadless()) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }
    else if (!checkGraphics()) {
      System.exit(NO_GRAPHICS);
    }

    if (!SystemInfo.isJavaVersionAtLeast("1.8")) {
      showMessage("Unsupported Java Version",
                  "Cannot start under Java " + SystemInfo.JAVA_RUNTIME_VERSION + ": Java 1.8 or later is required.", true);
      System.exit(UNSUPPORTED_JAVA_VERSION);
    }

    if (args.length == 0 || (args.length == 1 && "nosplash".equals(args[0]))) {
      try {
        installPatch();
      }
      catch (Throwable t) {
        showMessage("Update Failed", t);
        System.exit(UPDATE_FAILED);
      }
    }

    try {
      Bootstrap.main(args, Main.class.getName() + "Impl", "start");
    }
    catch (Throwable t) {
      showMessage("Start Failed", t);
      System.exit(STARTUP_EXCEPTION);
    }
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static void setFlags(String[] args) {
    isHeadless = isHeadless(args);
    isCommandLine = isCommandLine(args);
  }

  private static boolean isHeadless(String[] args) {
    if (Boolean.valueOf(System.getProperty(AWT_HEADLESS))) {
      return true;
    }

    if (args.length == 0) {
      return false;
    }

    String firstArg = args[0];
    return Comparing.strEqual(firstArg, "ant") ||
           Comparing.strEqual(firstArg, "duplocate") ||
           Comparing.strEqual(firstArg, "traverseUI") ||
           Comparing.strEqual(firstArg, "buildAppcodeCache") ||
           (firstArg.length() < 20 && firstArg.endsWith("inspect"));
  }

  private static boolean isCommandLine(String[] args) {
    if (isHeadless()) return true;
    return args.length > 0 && Comparing.strEqual(args[0], "diff");
  }

  private static boolean checkGraphics() {
    if (GraphicsEnvironment.isHeadless()) {
      showMessage("Startup Error", "Unable to detect graphics environment", true);
      return false;
    }

    return true;
  }

  public static boolean isUITraverser(final String[] args) {
    return args.length > 0 && Comparing.strEqual(args[0], "traverseUI");
  }

  private static boolean checkBundledJava(File java) throws Exception {
    String[] command = new String[]{java.getPath(), "-version"};
    try {
      Process process = Runtime.getRuntime().exec(command);
      String line = (new BufferedReader(new InputStreamReader(process.getErrorStream()))).readLine();
      if (line != null && (line.toLowerCase().startsWith("java version") || (line.toLowerCase().startsWith("openjdk version")))){
        int pos = line.indexOf('.');
        if (pos > 0){
          int majorVersion = Integer.parseInt(line.substring(pos-1, pos));
          int minorVersion = Integer.parseInt(line.substring(pos+1, pos+2));
          if (majorVersion > 1 || minorVersion > 5) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      System.out.println("updater: the java: " + command[0] + " is invalid.");
    }
    return false;
  }

  private static String getBundledJava(String javaHome) throws Exception {
    // remove the code after one cycle of EAP/patch
    File removeRestartFromHome = new File(System.getProperty("user.home") + "/." + System.getProperty("idea.paths.selector") + "/restart");
    if (removeRestartFromHome.exists()) {
      FileUtil.delete(removeRestartFromHome);
    }
    // remove the code after one cycle of EAP/patch

    String javaHomeCopy = Restarter.getRestarterDir() + "/jre";
    File javaCopy = SystemInfoRt.isWindows ? new File(javaHomeCopy + "/bin/java.exe") : new File(javaHomeCopy + "/bin/java");
    if (javaCopy != null && javaCopy.isFile() && checkBundledJava(javaCopy)) {
      javaHome = javaHomeCopy;
    }
    if (javaHome != javaHomeCopy) {
      File javaHomeCopyDir = new File(javaHomeCopy);
      if (javaHomeCopyDir.exists()) FileUtil.delete(javaHomeCopyDir);
      System.out.println("Updater: java: " + javaHome + " copied to " + javaHomeCopy);
      FileUtil.copyDir(new File(javaHome), javaHomeCopyDir);
      javaHome = javaHomeCopy;
    }
    return javaHome;
  }

  private static String getJava() throws Exception {
    String javaHome = System.getProperty("java.home");
    if (javaHome.toLowerCase().startsWith(PathManager.getHomePath().toLowerCase())) {
      System.out.println("Updater: uses bundled java.");
      javaHome = getBundledJava(javaHome);
    }
    return javaHome + "/bin/java";
  }

  private static void installPatch() throws Exception {
    String platform = System.getProperty(PLATFORM_PREFIX_PROPERTY, "idea");
    String patchFileName = ("jetbrains.patch.jar." + platform).toLowerCase(Locale.US);
    String tempDir = System.getProperty("java.io.tmpdir");
    File patch = new File(tempDir, patchFileName);

    // always delete previous patch copy
    String userName = System.getProperty("user.name");
    File patchCopy = new File(tempDir, patchFileName + "_copy_" + userName);
    File log4jCopy = new File(tempDir, "log4j.jar." + platform + "_copy_" + userName);
    File jnaUtilsCopy = new File(tempDir, "jna-platform.jar." + platform + "_copy_" + userName);
    File jnaCopy = new File(tempDir, "jna.jar." + platform + "_copy_" + userName);
    if (!FileUtilRt.delete(patchCopy) || !FileUtilRt.delete(log4jCopy) || !FileUtilRt.delete(jnaUtilsCopy) || !FileUtilRt.delete(jnaCopy)) {
      throw new IOException("Cannot delete temporary files in " + tempDir);
    }

    if (!patch.exists()) return;
    File log4j = new File(PathManager.getLibPath(), "log4j.jar");
    if (!log4j.exists()) throw new IOException("Log4J is missing: " + log4j);

    File jnaUtils = new File(PathManager.getLibPath(), "jna-platform.jar");
    if (!jnaUtils.exists()) throw new IOException("jna-platform.jar is missing: " + jnaUtils);

    File jna = new File(PathManager.getLibPath(), "jna.jar");
    if (!jna.exists()) throw new IOException("jna is missing: " + jna);

    copyFile(patch, patchCopy, true);
    copyFile(log4j, log4jCopy, false);
    copyFile(jna, jnaCopy, false);
    copyFile(jnaUtils, jnaUtilsCopy, false);

    int status = 0;
    if (Restarter.isSupported()) {
      List<String> args = new ArrayList<String>();

      if (SystemInfoRt.isWindows) {
        File launcher = new File(PathManager.getBinPath(), "VistaLauncher.exe");
        args.add(Restarter.createTempExecutable(launcher).getPath());
        Restarter.createTempExecutable(new File(PathManager.getBinPath(), "restarter.exe"));
      }

      //noinspection SpellCheckingInspection
      String java = getJava();
      Collections.addAll(args,
                         java,
                         "-Xmx750m",
                         "-Djna.nosys=true",
                         "-Djna.boot.library.path=",
                         "-Djna.debug_load=true",
                         "-Djna.debug_load.jna=true",
                         "-classpath",
                         patchCopy.getPath() + pathSeparator + log4jCopy.getPath() + pathSeparator + jnaCopy.getPath() + pathSeparator + jnaUtilsCopy.getPath(),
                         "-Djava.io.tmpdir=" + tempDir,
                         "-Didea.updater.log=" + PathManager.getLogPath(),
                         "-Dswing.defaultlaf=" + UIManager.getSystemLookAndFeelClassName(),
                         "com.intellij.updater.Runner",
                         "install",
                         PathManager.getHomePath());

      status = Restarter.scheduleRestart(ArrayUtilRt.toStringArray(args));
    }
    else {
      String message = "Patch update is not supported - please do it manually";
      showMessage("Update Error", message, true);
    }

    System.exit(status);
  }

  private static void copyFile(File original, File copy, boolean move) throws IOException {
    if (move) {
      if (!original.renameTo(copy) || !FileUtilRt.delete(original)) {
        throw new IOException("Cannot create temporary file: " + copy);
      }
    }
    else {
      FileUtilRt.copy(original, copy);
      if (!copy.exists()) {
        throw new IOException("Cannot create temporary file: " + copy);
      }
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public static void showMessage(String title, Throwable t) {
    StringWriter message = new StringWriter();

    AWTError awtError = findGraphicsError(t);
    if (awtError != null) {
      message.append("Failed to initialize graphics environment\n\n");
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append("Internal error. Please report to ");
      boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(PLATFORM_PREFIX_PROPERTY));
      message.append(studio ? "https://code.google.com/p/android/issues" : "http://jb.gg/ide/critical-startup-errors");
      message.append("\n\n");
    }

    t.printStackTrace(new PrintWriter(message));
    showMessage(title, message.toString(), true);
  }

  private static AWTError findGraphicsError(Throwable t) {
    while (t != null) {
      if (t instanceof AWTError) {
        return (AWTError)t;
      }
      t = t.getCause();
    }
    return null;
  }

  @SuppressWarnings({"UseJBColor", "UndesirableClassUsage", "UseOfSystemOutOrSystemErr"})
  public static void showMessage(String title, String message, boolean error) {
    PrintStream stream = error ? System.err : System.out;
    stream.println("\n" + title + ": " + message);

    boolean headless = !hasGraphics || isCommandLine() || GraphicsEnvironment.isHeadless();
    if (!headless) {
      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
      catch (Throwable ignore) { }

      try {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setText(message.replaceAll("\t", "    "));
        textPane.setBackground(UIUtil.getPanelBackground());
        textPane.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
          textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        int maxHeight = Math.min(JBUI.scale(600), Toolkit.getDefaultToolkit().getScreenSize().height - 150);
        int maxWidth = Math.min(JBUI.scale(600), Toolkit.getDefaultToolkit().getScreenSize().width - 150);
        Dimension component = scrollPane.getPreferredSize();
        if (component.height > maxHeight || component.width > maxWidth) {
          scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, component.width), Math.min(maxHeight, component.height)));
        }

        int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
      }
      catch (Throwable t) {
        stream.println("\nAlso, an UI exception occurred on attempt to show above message:");
        t.printStackTrace(stream);
      }
    }
  }
}