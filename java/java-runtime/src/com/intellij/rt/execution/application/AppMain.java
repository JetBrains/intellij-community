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
package com.intellij.rt.execution.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

/**
 * @author ven
 * @noinspection UseOfSystemOutOrSystemErr
 */
public class AppMain {
  private static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  private static final String PROPERTY_BIN_PATH = "idea.launcher.bin.path";
  private static final String JAVAFX_LAUNCHER = "com.sun.javafx.application.LauncherImpl";
  private static final String LAUNCH_APPLICATION_METHOD_NAME = "launchApplication";

  private static native void triggerControlBreak();

  private static boolean ourHasSecurityProblem = false;
  static {
    try {
      String binPath = System.getProperty(PROPERTY_BIN_PATH) + File.separator;
      String osName = System.getProperty("os.name").toLowerCase(Locale.US);
      String arch = System.getProperty("os.arch").toLowerCase(Locale.US);
      String libPath = null;
      if (osName.startsWith("windows")) {
        libPath = binPath + (arch.equals("amd64") ? "breakgen64.dll" : "breakgen.dll");
      }
      else if (osName.startsWith("linux")) {
        libPath = binPath + (arch.equals("amd64") ? "libbreakgen64.so" : "libbreakgen.so");
      }
      else if (osName.startsWith("mac")) {
        libPath = binPath + (arch.endsWith("64") ? "libbreakgen64.jnilib" : "libbreakgen.jnilib");
      }
      if (libPath != null) {
        System.load(libPath);
      }
    }
    catch (UnsatisfiedLinkError e) {
      //Do nothing, unknown os or some other error => no ctrl-break is available
    }
    catch (SecurityException e) {
      ourHasSecurityProblem = true;
      System.out.println("break in console is not supported due to security permissions: " + e.getMessage());
    }
  }

  public static void main(String[] args) throws Throwable {
    if (!ourHasSecurityProblem) {
      final int portNumber = Integer.getInteger(PROPERTY_PORT_NUMBER).intValue();
      Thread t = new Thread(
        new Runnable() {
          public void run() {
            try {
              ServerSocket socket = new ServerSocket(portNumber);
              try {
                Socket client = socket.accept();
                try {
                  BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                  try {
                    while (true) {
                      String msg = reader.readLine();
                      if ("TERM".equals(msg)) {
                        return;
                      }
                      else if ("BREAK".equals(msg)) {
                        triggerControlBreak();
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
              finally {
                socket.close();
              }
            }
            catch (IOException ignored) { }
            catch (IllegalArgumentException ignored) { }
            catch (SecurityException ignored) { }
          }
        }, "Monitor Ctrl-Break");
      try {
        t.setDaemon(true);
        t.start();
      } catch (Exception ignored) { }
    }

    String mainClass = args[0];
    String[] params = new String[args.length - 1];
    System.arraycopy(args, 1, params, 0, args.length - 1);

    Class appClass = Class.forName(mainClass);
    Method m;
    try {
      m = appClass.getMethod("main", new Class[]{params.getClass()});
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

  private static boolean startJavaFXApplication(String[] params, Class appClass) throws NoSuchMethodException {
    try {
      //check in launch method for application class in the stack trace leads to this hack here
      Class[] types = {appClass.getClass(), params.getClass()};
      Method launchApplication = Class.forName(JAVAFX_LAUNCHER).getMethod(LAUNCH_APPLICATION_METHOD_NAME, types);
      launchApplication.invoke(null, new Object[] {appClass, params});
      return true;
    }
    catch (Throwable e) {
      return false;
    }
  }
}