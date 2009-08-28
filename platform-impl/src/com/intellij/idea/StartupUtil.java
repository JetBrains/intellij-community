package com.intellij.idea;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.ide.startupWizard.StartupWizard;
import com.intellij.ide.plugins.PluginManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * @author yole
 */
public class StartupUtil {
  static boolean isHeadless;
  static SocketLock ourLock;
  private static String myDefaultLAF;

  private StartupUtil() {
  }

  public static void setDefaultLAF(String laf) {
    myDefaultLAF = laf;
  }

  public static String getDefaultLAF() {
    return myDefaultLAF;
  }

  public static boolean shouldShowSplash(final String[] args) {
    @NonNls final String nosplashCode = "nosplash";
    return !Arrays.asList(args).contains(nosplashCode);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  /**
   * Checks if the program can run under the JDK it was started with
   */
  static boolean checkJdkVersion() {
    if (!"true".equals(System.getProperty("idea.no.jre.check"))) {
      try {
        // try to find a class from tools.jar
        Class.forName("com.sun.jdi.Field");
      }
      catch (ClassNotFoundException e) {
        if (isHeadless()) { //team server inspections
          System.out.println("tools.jar is not in " + ApplicationNamesInfo.getInstance().getProductName() + " classpath. Please ensure JAVA_HOME points to JDK rather than JRE");
          return false;
        }
        try {
          final Runnable runnable = new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), "tools.jar is not in " +
                                                                        ApplicationNamesInfo.getInstance().getProductName() +
                                                                        " classpath. Please ensure JAVA_HOME points to JDK rather than JRE",
                                            "Error", JOptionPane.ERROR_MESSAGE);
            }
          };
          if(EventQueue.isDispatchThread()) {
            runnable.run();
          } else {
            EventQueue.invokeAndWait(runnable);
          }
        } catch(Exception ex) {
          // do nothing
        }
        return false;
      }
    }

    if ("true".equals(System.getProperty("idea.no.jdk.check"))) return true;

    String version = System.getProperty("java.version");

    if (version.startsWith("1.5") || version.startsWith("1.6")) {
      return true;
    }

    showVersionMismatch(version);
    return false;
  }

  private static void showVersionMismatch(final String version) {
    if (isHeadless()) { //team server inspections
      System.out.println("The JDK version is " + version + " but " + ApplicationNamesInfo.getInstance().getProductName() + " requires JDK 1.5 or 1.6");
      return;
    }
    JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                  "The JDK version is " + version + "\n" + ApplicationNamesInfo.getInstance().getProductName() +
                                  " requires JDK 1.5 or 1.6",
                                  "Java Version Mismatch",
                                  JOptionPane.INFORMATION_MESSAGE);
  }

  synchronized static boolean lockSystemFolders() {
    if (ourLock == null) {
      ourLock = new SocketLock();
    }

    boolean locked = ourLock.lock(PathManager.getConfigPath(false)) && ourLock.lock(PathManager.getSystemPath());

    if (!locked) {
      if (isHeadless()) { //team server inspections
        System.out.println("Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.");
        return false;
      }
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                    "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() +
                                    " can be run at a time.",
                                    "Error",
                                    JOptionPane.INFORMATION_MESSAGE);
    }

    return locked;
  }

  static boolean checkStartupPossible() {
    return checkJdkVersion() && lockSystemFolders();
  }

  static void runStartupWizard() {
    final java.util.List<ApplicationInfoEx.PluginChooserPage> pages = ApplicationInfoImpl.getShadowInstance().getPluginChooserPages();
    if (!pages.isEmpty()) {
      final StartupWizard startupWizard = new StartupWizard(pages);
      startupWizard.setCancelText("Skip");
      startupWizard.show();
      PluginManager.invalidatePlugins();
    }
  }
}
