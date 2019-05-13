// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public class Main {
  public static final int NO_GRAPHICS = 1;
  public static final int RESTART_FAILED = 2;
  public static final int STARTUP_EXCEPTION = 3;
  public static final int JDK_CHECK_FAILED = 4;
  public static final int DIR_CHECK_FAILED = 5;
  public static final int INSTANCE_CHECK_FAILED = 6;
  public static final int LICENSE_ERROR = 7;
  public static final int PLUGIN_ERROR = 8;
  public static final int OUT_OF_MEMORY = 9;
  @SuppressWarnings("unused") // left for compatibility and reserved for future use
  public static final int UNSUPPORTED_JAVA_VERSION = 10;
  public static final int PRIVACY_POLICY_REJECTION = 11;
  public static final int INSTALLATION_CORRUPTED = 12;

  private static final String AWT_HEADLESS = "java.awt.headless";
  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final String[] NO_ARGS = {};
  private static final List<String> HEADLESS_COMMANDS = Arrays.asList(
    "ant", "duplocate", "traverseUI", "buildAppcodeCache", "format", "keymap", "update", "inspections", "intentions");
  private static final List<String> GUI_COMMANDS = Arrays.asList("diff", "merge");

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean hasGraphics = true;

  private Main() { }

  public static void main(String[] args) {
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
    if (isHeadless()) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }
  }

  public static boolean isHeadless(String[] args) {
    if (Boolean.valueOf(System.getProperty(AWT_HEADLESS))) {
      return true;
    }

    if (args.length == 0) {
      return false;
    }

    String firstArg = args[0];
    return HEADLESS_COMMANDS.contains(firstArg) || firstArg.length() < 20 && firstArg.endsWith("inspect");
  }

  private static boolean isCommandLine(String[] args) {
    return isHeadless(args) || args.length > 0 && GUI_COMMANDS.contains(args[0]);
  }

  private static boolean checkGraphics() {
    if (GraphicsEnvironment.isHeadless()) {
      showMessage("Startup Error", "Unable to detect graphics environment", true);
      return false;
    }

    return true;
  }

  public static boolean isApplicationStarterForBuilding(final String[] args) {
    return args.length > 0 && (Comparing.strEqual(args[0], "traverseUI") ||
                               Comparing.strEqual(args[0], "listBundledPlugins") ||
                               Comparing.strEqual(args[0], "buildAppcodeCache"));
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
        stream.println("\nAlso, an UI exception occurred on attempt to show above message:");
        t.printStackTrace(stream);
      }
    }
  }
}