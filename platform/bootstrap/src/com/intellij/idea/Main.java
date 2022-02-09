// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.lang.PathClassLoader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
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
  // reserved (doesn't seem to ever be used): public static final int OUT_OF_MEMORY = 9;
  // reserved (permanently if launchers will perform the check): public static final int UNSUPPORTED_JAVA_VERSION = 10;
  public static final int PRIVACY_POLICY_REJECTION = 11;
  public static final int INSTALLATION_CORRUPTED = 12;
  public static final int ACTIVATE_WRONG_TOKEN_CODE = 13;
  public static final int ACTIVATE_NOT_INITIALIZED = 14;
  public static final int ACTIVATE_ERROR = 15;
  public static final int ACTIVATE_DISPOSING = 16;

  public static final String FORCE_PLUGIN_UPDATES = "idea.force.plugin.updates";
  public static final String CWM_HOST_COMMAND = "cwmHost";
  public static final String CWM_HOST_NO_LOBBY_COMMAND = "cwmHostNoLobby";

  private static final String MAIN_RUNNER_CLASS_NAME = "com.intellij.idea.StartupUtil";
  private static final String AWT_HEADLESS = "java.awt.headless";
  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final List<String> HEADLESS_COMMANDS = List.of(
    "ant", "duplocate", "dump-shared-index", "traverseUI", "buildAppcodeCache", "format", "keymap", "update", "inspections", "intentions",
    "rdserver-headless", "thinClient-headless", "installPlugins", "dumpActions", "cwmHostStatus", "warmup", "buildEventsScheme",
    "remoteDevShowHelp", "installGatewayProtocolHandler", "uninstallGatewayProtocolHandler", "appcodeClangModulesDiff");
  private static final List<String> GUI_COMMANDS = List.of("diff", "merge");

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean hasGraphics = true;
  private static boolean isLightEdit;

  private Main() { }

  public static void main(String[] args) {
    LinkedHashMap<String, Long> startupTimings = new LinkedHashMap<>(6);
    startupTimings.put("startup begin", System.nanoTime());

    if (args.length == 1 && "%f".equals(args[0])) {
      //noinspection SSBasedInspection
      args = new String[0];
    }

    setFlags(args);

    try {
      bootstrap(args, startupTimings);
    }
    catch (Throwable t) {
      showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
      System.exit(STARTUP_EXCEPTION);
    }
  }

  private static void bootstrap(String[] args, LinkedHashMap<String, Long> startupTimings) throws Throwable {
    startupTimings.put("properties loading", System.nanoTime());
    PathManager.loadProperties();

    startupTimings.put("plugin updates install", System.nanoTime());
    // this check must be performed before system directories are locked
    if (!isCommandLine || Boolean.getBoolean(FORCE_PLUGIN_UPDATES)) {
      boolean configImportNeeded = !isHeadless() && !Files.exists(Path.of(PathManager.getConfigPath()));
      if (!configImportNeeded) {
        installPluginUpdates();
      }
    }

    startupTimings.put("classloader init", System.nanoTime());
    PathClassLoader newClassLoader = BootstrapClassLoaderUtil
      .initClassLoader(args.length > 0 && (CWM_HOST_COMMAND.equals(args[0]) || CWM_HOST_NO_LOBBY_COMMAND.equals(args[0])));
    Thread.currentThread().setContextClassLoader(newClassLoader);

    startupTimings.put("MainRunner search", System.nanoTime());

    Class<?> mainClass = newClassLoader.loadClassInsideSelf(MAIN_RUNNER_CLASS_NAME, "com/intellij/idea/StartupUtil.class",
                                                            -635775336887217634L, true);
    if (mainClass == null) {
      throw new ClassNotFoundException(MAIN_RUNNER_CLASS_NAME);
    }

    WindowsCommandLineProcessor.ourMainRunnerClass = mainClass;
    MethodHandles.lookup()
      .findStatic(mainClass, "start", MethodType.methodType(void.class, String.class,
                                                            boolean.class, boolean.class,
                                                            String[].class, LinkedHashMap.class))
      .invokeExact(Main.class.getName() + "Impl", isHeadless, newClassLoader != Main.class.getClassLoader(), args, startupTimings);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void installPluginUpdates() {
    try {
      // referencing StartupActionScriptManager.ACTION_SCRIPT_FILE is ok - string constant will be inlined
      Path scriptFile = Path.of(PathManager.getPluginTempPath(), StartupActionScriptManager.ACTION_SCRIPT_FILE);
      if (Files.isRegularFile(scriptFile)) {
        // load StartupActionScriptManager and all others related class (ObjectInputStream and so on loaded as part of class define)
        // only if there is action script to execute
        StartupActionScriptManager.executeActionScript();
      }
    }
    catch (IOException e) {
      showMessage("Plugin Installation Error",
                  "The IDE failed to install or update some plugins.\n" +
                  "Please try again, and if the problem persists, please report it\n" +
                  "to https://jb.gg/ide/critical-startup-errors\n\n" +
                  "The cause: " + e, false);
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
    isLightEdit = "LightEdit".equals(System.getProperty(PLATFORM_PREFIX_PROPERTY)) || (!isCommandLine && isFileAfterOptions(args));
  }

  private static boolean isFileAfterOptions(String @NotNull [] args) {
    for (String arg : args) {
      if (!arg.startsWith("-")) { // If not an option
        try {
          Path path = Path.of(arg);
          return Files.isRegularFile(path) || !Files.exists(path);
        }
        catch (Throwable t) {
          return false;
        }
      }
      else if (arg.equals("-l") || arg.equals("--line") || arg.equals("-c") || arg.equals("--column")) { // NON-NLS
        return true;
      }
    }
    return false;
  }

  @TestOnly
  public static void setHeadlessInTestMode(boolean headless) {
    isHeadless = headless;
    isCommandLine = true;
    isLightEdit = false;
  }

  private static boolean isHeadless(String[] args) {
    if (Boolean.getBoolean(AWT_HEADLESS)) {
      return true;
    }

    if (args.length == 0) {
      return false;
    }

    String firstArg = args[0];
    return HEADLESS_COMMANDS.contains(firstArg) || firstArg.length() < 20 && firstArg.endsWith("inspect"); //NON-NLS
  }

  public static void showMessage(@Nls(capitalization = Nls.Capitalization.Title) String title, Throwable t) {
    @Nls(capitalization = Nls.Capitalization.Sentence) StringWriter message = new StringWriter();

    AWTError awtError = findGraphicsError(t);
    if (awtError != null) {
      message.append(BootstrapBundle.message("bootstrap.error.message.failed.to.initialize.graphics.environment"));
      message.append("\n\n");
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append(BootstrapBundle.message("bootstrap.error.message.internal.error.please.refer.to.0", supportUrl()));
      message.append("\n\n");
    }

    t.printStackTrace(new PrintWriter(message));

    message.append("\n-----\n").append(BootstrapBundle.message("bootstrap.error.message.jre.details", jreDetails()));

    showMessage(title, message.toString(), true); //NON-NLS
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

  private static @NlsSafe String jreDetails() {
    Properties sp = System.getProperties();
    String jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
    String vendor = sp.getProperty("java.vendor", "(unknown vendor)");
    String arch = sp.getProperty("os.arch", "(unknown arch)");
    String home = sp.getProperty("java.home", "(unknown java.home)");
    return jre + ' ' + arch + " (" + vendor + ")\n" + home;
  }

  private static @NlsSafe String supportUrl() {
    boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(PLATFORM_PREFIX_PROPERTY));
    return studio ? "https://code.google.com/p/android/issues" : "https://jb.gg/ide/critical-startup-errors";
  }

  @SuppressWarnings({"UndesirableClassUsage", "UseOfSystemOutOrSystemErr"})
  public static void showMessage(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                 @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                 boolean error) {
    PrintStream stream = error ? System.err : System.out;
    stream.println();
    stream.println(title);
    stream.println(message);

    boolean headless = !hasGraphics || isCommandLine() || GraphicsEnvironment.isHeadless();
    if (headless) return;

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Throwable ignore) { }

    try {
      JTextPane textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setText(message.replaceAll("\t", "    "));
      textPane.setBackground(UIManager.getColor("Panel.background"));
      textPane.setCaretPosition(0);
      JScrollPane scrollPane =
        new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
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
      stream.println("\nAlso, a UI exception occurred on an attempt to show the above message");
      t.printStackTrace(stream);
    }
  }
}
