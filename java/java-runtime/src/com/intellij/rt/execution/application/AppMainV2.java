// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.util.Locale;

/**
 * @author ven
 * @noinspection UseOfSystemOutOrSystemErr
 */
public final class AppMainV2 {
  public static final String LAUNCHER_PORT_NUMBER = "idea.launcher.port";
  public static final String LAUNCHER_BIN_PATH = "idea.launcher.bin.path";

  private static native void triggerControlBreak();

  private static boolean loadHelper(String binPath) {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    if (osName.startsWith("windows")) {
      String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
      File libFile = new File(binPath, arch.equals("amd64") ? "breakgen64.dll" : "breakgen.dll");
      if (libFile.isFile()) {
        System.load(libFile.getAbsolutePath());
        return true;
      }
    }

    return false;
  }

  private static void startMonitor(final int portNumber, final boolean helperLibLoaded) {
    Thread t = new Thread("Monitor Ctrl-Break") {
      public void run() {
        try {
          Socket client = new Socket("127.0.0.1", portNumber);
          try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "US-ASCII"));
            try {
              while (true) {
                String msg = reader.readLine();
                if (msg == null || "TERM".equals(msg)) {
                  return;
                }
                else if ("BREAK".equals(msg)) {
                  if (helperLibLoaded) {
                    triggerControlBreak();
                  }
                }
                else if ("STOP".equals(msg)) {
                  System.exit(1);
                }
              }
            }
            finally {
              reader.close();
            }
          }
          finally {
            client.close();
          }
        }
        catch (Exception ignored) { }
      }
    };
    t.setDaemon(true);
    t.start();
  }

  public static void main(String[] args) throws Throwable {
    try {
      boolean helperLibLoaded = loadHelper(System.getProperty(LAUNCHER_BIN_PATH));
      int portNumber = Integer.parseInt(System.getProperty(LAUNCHER_PORT_NUMBER));
      startMonitor(portNumber, helperLibLoaded);
    }
    catch (Throwable t) {
      System.err.println("Launcher failed - \"Dump Threads\" and \"Exit\" actions are unavailable (" + t.getMessage() + ')');
    }

    String mainClass = args[0];
    String[] params = new String[args.length - 1];
    System.arraycopy(args, 1, params, 0, args.length - 1);

    Class<?> appClass = Class.forName(mainClass);
    Method m;
    try {
      m = appClass.getMethod("main", String[].class);
    }
    catch (NoSuchMethodException e) {
      if (!startJavaFXApplication(params, appClass)) {
        throw e;
      }
      return;
    }

    if (!Modifier.isStatic(m.getModifiers())) {
      System.err.println("main method should be static");
      return;
    }

    if (!void.class.isAssignableFrom(m.getReturnType())) {
      System.err.println("main method must return a value of type void");
      return;
    }

    try {
      m.setAccessible(true);
      m.invoke(null, new Object[]{params});
    }
    catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private static boolean startJavaFXApplication(String[] params, Class<?> appClass) {
    try {
      //check in launch method for application class in the stack trace leads to this hack here
      Method launchApplication = Class.forName("com.sun.javafx.application.LauncherImpl")
        .getMethod("launchApplication", Class.class, String[].class);
      launchApplication.invoke(null, appClass, params);
      return true;
    }
    catch (Throwable e) {
      return false;
    }
  }

  public static final class Agent {
    public static void premain(String args, Instrumentation i) {
      AppMainV2.premain(args);
    }
  }

  // todo[r.sh] inline some time after 2017.1.1 release
  public static void premain(String args) {
    try {
      int p = args.indexOf(':');
      if (p < 0) throw new IllegalArgumentException("incorrect parameter: " + args);
      boolean helperLibLoaded = loadHelper(args.substring(p + 1));
      int portNumber = Integer.parseInt(args.substring(0, p));
      startMonitor(portNumber, helperLibLoaded);
    }
    catch (Throwable t) {
      System.err.println("Launcher failed - \"Dump Threads\" and \"Exit\" actions are unavailable (" + t.getMessage() + ')');
    }
  }
}