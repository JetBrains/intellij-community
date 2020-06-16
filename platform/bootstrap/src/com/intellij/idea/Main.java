// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

public final class Main {
  public static final int NO_GRAPHICS = 1;
  public static final int RESTART_FAILED = 2;
  public static final int STARTUP_EXCEPTION = 3;
  public static final int JDK_CHECK_FAILED = 4;
  public static final int DIR_CHECK_FAILED = 5;
  public static final int INSTANCE_CHECK_FAILED = 6;
  public static final int LICENSE_ERROR = 7;
  public static final int PLUGIN_ERROR = 8;
  public static final int OUT_OF_MEMORY = 9;
  @SuppressWarnings("unused") public static final int UNSUPPORTED_JAVA_VERSION = 10;  // left for compatibility/reserved for future use
  public static final int PRIVACY_POLICY_REJECTION = 11;
  public static final int INSTALLATION_CORRUPTED = 12;
  public static final int ACTIVATE_WRONG_TOKEN_CODE = 13;
  public static final int ACTIVATE_NOT_INITIALIZED = 14;
  public static final int ACTIVATE_ERROR = 15;
  public static final int ACTIVATE_DISPOSING = 16;

  public static final String FORCE_PLUGIN_UPDATES = "idea.force.plugin.updates";

  private static final String AWT_HEADLESS = "java.awt.headless";
  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final String[] NO_ARGS = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private static final List<String> HEADLESS_COMMANDS = Arrays.asList(
    "ant", "duplocate", "dump-shared-index", "traverseUI", "buildAppcodeCache", "format", "keymap", "update", "inspections", "intentions",
    "rdserver-headless", "thinClient-headless");
  private static final List<String> GUI_COMMANDS = Arrays.asList("diff", "merge");

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean hasGraphics = true;
  private static boolean isLightEdit;

  private Main() { }

  public static void main(String[] args) {
    LinkedHashMap<String, Long> startupTimings = new LinkedHashMap<>();
    startupTimings.put("startup begin", System.nanoTime());

    if (args.length == 1 && "%f".equals(args[0])) {
      args = NO_ARGS;
    }

    if (args.length == 1 && args[0].startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(args[0]);
      args = NO_ARGS;
    }

    setFlags(args);

    if (!isHeadless() && !checkGraphics()) {
      System.exit(NO_GRAPHICS);
    }

    try {
      bootstrap(args, startupTimings);
    }
    catch (Throwable t) {
      showMessage("Start Failed", t);
      System.exit(STARTUP_EXCEPTION);
    }
  }

  private static void bootstrap(String[] args, LinkedHashMap<String, Long> startupTimings) throws Exception {
    startupTimings.put("properties loading", System.nanoTime());
    PathManager.loadProperties();

    // this check must be performed before system directories are locked
    String configPath = PathManager.getConfigPath();
    boolean configImportNeeded = !isHeadless() && !Files.exists(Paths.get(configPath));
    if (!configImportNeeded) {
      installPluginUpdates();
    }

    startupTimings.put("classloader init", System.nanoTime());
    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader();
    Thread.currentThread().setContextClassLoader(newClassLoader);

    startupTimings.put("MainRunner search", System.nanoTime());
    Class<?> klass = Class.forName("com.intellij.ide.plugins.MainRunner", true, newClassLoader);
    WindowsCommandLineProcessor.ourMainRunnerClass = klass;
    Method startMethod = klass.getMethod("start", String.class, String[].class, LinkedHashMap.class);
    startMethod.setAccessible(true);
    startMethod.invoke(null, Main.class.getName() + "Impl", args, startupTimings);
  }

  private static void installPluginUpdates() {
    if (!isCommandLine() || Boolean.getBoolean(FORCE_PLUGIN_UPDATES)) {
      try {
        StartupActionScriptManager.executeActionScript();
      }
      catch (IOException e) {
        String message =
          "The IDE failed to install some plugins.\n\n" +
          "Most probably, this happened because of a change in a serialization format.\n" +
          "Please try again, and if the problem persists, please report it\n" +
          "to http://jb.gg/ide/critical-startup-errors" +
          "\n\nThe cause: " + e.getMessage();
        showMessage("Plugin Installation Error", message, false);
      }
    }
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static boolean isLightEdit() {
    return isLightEdit;
  }

  public static void setFlags(String @NotNull [] args) {
    isHeadless = isHeadless(args);
    isCommandLine = isHeadless || (args.length > 0 && GUI_COMMANDS.contains(args[0]));
    if (isHeadless) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }

    boolean isFirstArgRegularFile;
    try {
      isFirstArgRegularFile = args.length > 0 && Files.isRegularFile(Paths.get(args[0]));
    }
    catch (Throwable t) {
      isFirstArgRegularFile = false;
    }

    isLightEdit = "LightEdit".equals(System.getProperty(PLATFORM_PREFIX_PROPERTY)) || isFirstArgRegularFile;
  }

  @TestOnly
  public static void setHeadlessInTestMode(boolean isHeadless) {
    Main.isHeadless = isHeadless;
    isCommandLine = true;
    isLightEdit = false;
  }

  public static boolean isHeadless(String @NotNull [] args) {
    if (Boolean.getBoolean(AWT_HEADLESS)) {
      return true;
    }

    if (args.length == 0) {
      return false;
    }

    String firstArg = args[0];
    return HEADLESS_COMMANDS.contains(firstArg) || firstArg.length() < 20 && firstArg.endsWith("inspect");
  }

  private static boolean checkGraphics() {
    if (GraphicsEnvironment.isHeadless()) {
      showMessage("Startup Error", "Unable to detect graphics environment", true);
      return false;
    }

    return true;
  }

  public static void showMessage(String title, Throwable t) {
    StringWriter message = new StringWriter();

    AWTError awtError = findGraphicsError(t);
    if (awtError != null) {
      message.append("Failed to initialize graphics environment\n\n");
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append("Internal error. Please refer to ");
      boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(PLATFORM_PREFIX_PROPERTY));
      message.append(studio ? "https://code.google.com/p/android/issues" : "http://jb.gg/ide/critical-startup-errors");
      message.append("\n\n");
    }

    t.printStackTrace(new PrintWriter(message));

    Properties sp = System.getProperties();
    String jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
    String vendor = sp.getProperty("java.vendor", "(unknown vendor)");
    String arch = sp.getProperty("os.arch", "(unknown arch)");
    String home = sp.getProperty("java.home", "(unknown java.home)");
    message.append("\n-----\nJRE ").append(jre).append(' ').append(arch).append(" by ").append(vendor).append('\n').append(home);

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

  @SuppressWarnings({"UndesirableClassUsage", "UseOfSystemOutOrSystemErr"})
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
        textPane.setBackground(UIManager.getColor("Panel.background"));
        textPane.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
          textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
        int maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;
        Dimension component = scrollPane.getPreferredSize();
        if (component.height > maxHeight || component.width > maxWidth) {
          scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, component.width), Math.min(maxHeight, component.height)));
        }

        int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
      }
      catch (Throwable t) {
        stream.println("\nAlso, a UI exception occurred on an attempt to show the above message:");
        t.printStackTrace(stream);
      }
    }
  }
}