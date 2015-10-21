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

/**
 * @author ven
 * @noinspection HardCodedStringLiteral
 */
public class AppMain {
  private static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  private static final String PROPERTY_BINPATH = "idea.launcher.bin.path";

  private static final String JAVAFX_LAUNCHER = "com.sun.javafx.application.LauncherImpl";
  private static final String LAUNCH_APPLICATION_METHOD_NAME = "launchApplication";

  private static native void triggerControlBreak();

  private static boolean ourHasSecurityProblem = false;
  static {
    try {
      String binPath = System.getProperty(PROPERTY_BINPATH) + File.separator;
      final String osName = System.getProperty("os.name").toLowerCase();
      String arch = System.getProperty("os.arch").toLowerCase();
      String libPath = null;
      if (osName.startsWith("windows")) {
        if (arch.equals("amd64")) {
          libPath = binPath + "breakgen64.dll";
        }
        else {
          libPath = binPath + "breakgen.dll";
        }
      } else if (osName.startsWith("linux")) {
        if (arch.equals("amd64")) {
          libPath = binPath + "libbreakgen64.so";
        } else {
          libPath = binPath + "libbreakgen.so";
        }
      } else if (osName.startsWith("mac")) {
        if (arch.endsWith("64")) {
          libPath = binPath + "libbreakgen64.jnilib";
        } else {
          libPath = binPath + "libbreakgen.jnilib";
        }
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
              Socket client = socket.accept();
              BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
              while (true) {
                String msg = reader.readLine();

                if ("TERM".equals(msg)){
                  return;
                }
                else if ("BREAK".equals(msg)) {
                  triggerControlBreak();
                }
                else if ("STOP".equals(msg)) {
                  System.exit(1);
                }
              }
            } catch (IOException ignored) {
            } catch (IllegalArgumentException ignored) {
            } catch (SecurityException ignored) {
            }
          }
        }, "Monitor Ctrl-Break");
      try {
        t.setDaemon(true);
        t.start();
      } catch (Exception ignored) {}
    }

    String mainClass = args[0];
    String[] parms = new String[args.length - 1];
    for (int j = 1; j < args.length; j++) {
      parms[j - 1] = args[j];
    }
    final Class appClass = Class.forName(mainClass);
    Method m;
    try {
      m = appClass.getMethod("main", new Class[]{parms.getClass()});
    }
    catch (NoSuchMethodException e) {
      if (!startJavaFXApplication(parms, appClass)) {
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
      ensureAccess(m);
      m.invoke(null, new Object[]{parms});
    } catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private static boolean startJavaFXApplication(String[] parms, Class appClass) throws NoSuchMethodException {
    try {
      //check in launch method for application class in the stack trace leads to this hack here
      final Method launchApplication = Class.forName(JAVAFX_LAUNCHER).getMethod(LAUNCH_APPLICATION_METHOD_NAME,
                                                                                new Class[]{appClass.getClass(), parms.getClass()});
      launchApplication.invoke(null, new Object[] {appClass, parms});
      return true;
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static void ensureAccess(Object reflectionObject) {
    // need to call setAccessible here in order to be able to launch package-local classes
    // calling setAccessible() via reflection because the method is missing from java version 1.1.x
    final Class aClass = reflectionObject.getClass();
    try {
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", new Class[] {boolean.class});
      setAccessibleMethod.invoke(reflectionObject, new Object[] {Boolean.TRUE});
    }
    catch (Exception e) {
      // the method not found
    }
  }
}
