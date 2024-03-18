// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.util.*;

/**
 * @noinspection UseOfSystemOutOrSystemErr, CharsetObjectCanBeUsed
 */
public final class AppMainV2 {
  public static final String LAUNCHER_PORT_NUMBER = "idea.launcher.port";
  public static final String LAUNCHER_BIN_PATH = "idea.launcher.bin.path";
  public static final String LAUNCHER_USE_JDK_21_PREVIEW = "idea.launcher.use.21.preview";

  private static native void triggerControlBreak();

  private static boolean loadHelper(String binPath) {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    if (osName.startsWith("windows")) {
      String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
      //noinspection SpellCheckingInspection
      String libName = "x86_64".equals(arch) || "amd64".equals(arch) ? "breakgen64.dll" :
                       "aarch64".equals(arch) || "arm64".equals(arch) ? "breakgen64a.dll" :
                       "i386".equals(arch) || "x86".equals(arch) ? "breakgen.dll" :
                       null;  // see also: `ProcessProxyImpl#canSendBreak`
      if (libName != null) {
        File libFile = new File(binPath, libName);
        if (libFile.isFile()) {
          System.load(libFile.getAbsolutePath());
          return true;
        }
      }
    }

    return false;
  }

  private static void startMonitor(final int portNumber, final boolean helperLibLoaded) {
    Thread t = new Thread("Monitor Ctrl-Break") {
      @Override
      public void run() {
        try {
          try (Socket client = new Socket("127.0.0.1", portNumber)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "US-ASCII"))) {
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
    String[] params = Arrays.copyOfRange(args, 1, args.length);

    Class<?> appClass = Class.forName(mainClass);
    Method m = findMethodToRun(appClass);
    if (m == null) {
      try {
        // left for compatibility reasons and as a fallback
        m = appClass.getMethod("main", String[].class);
      } catch (NoSuchMethodException e) {
        if (!startJavaFXApplication(params, appClass)) {
          throw new IllegalArgumentException("Main method is not found");
        }
        return;
      }
    }

    if (!void.class.isAssignableFrom(m.getReturnType())) {
      System.err.println("main method must return a value of type void");
      return;
    }

    try {
      m.setAccessible(true);
      int parameterCount = m.getParameterTypes().length;
      Object objInstance = null;
      if (!Modifier.isStatic(m.getModifiers())) {
        Constructor<?> declaredConstructor;
        try {
          declaredConstructor = appClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
          System.err.println("Non-static main() method was invoked: class must have constructor with no parameters");
          return;
        }
        declaredConstructor.setAccessible(true);
        objInstance = declaredConstructor.newInstance();
      }
      if (parameterCount == 0) {
        m.invoke(objInstance);
      } else {
        m.invoke(objInstance, new Object[]{params});
      }
    }
    catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private enum MainMethodSearchMode{
    ALL_METHOD, STATIC_METHOD, NON_STATIC_METHOD
  }
  /**
   * Retrieves the status of the main method.
   *
   * @param method     method to check
   * @param mode     indicates which methods are considered
   * @return the status of the main method. Can be one of the following:
   * - MainMethodStatus.NotMain: the method is not the main method
   * - MainMethodStatus.WithArgs: the method is the main method and accepts an array of strings as parameter
   * - MainMethodStatus.WithoutArgs: the method is the main method and does not accept any parameters
   */
  private static MainMethodStatus getMainMethodStatus(Method method, MainMethodSearchMode mode) {
    if ("main".equals(method.getName()) ) {
      if (!Modifier.isPrivate(method.getModifiers())) {
        if (mode == MainMethodSearchMode.ALL_METHOD ||
            (mode == MainMethodSearchMode.STATIC_METHOD && Modifier.isStatic(method.getModifiers()))) {
          Class<?>[] parameterTypes = method.getParameterTypes();
          if (parameterTypes.length == 1 && parameterTypes[0] == String[].class) {
            return MainMethodStatus.WithArgs;
          }
          if (parameterTypes.length == 0) {
            return MainMethodStatus.WithoutArgs;
          }
        }
      }
    }
    return MainMethodStatus.NotMain;
  }

  /**
   * @return true if the Java version is "21", false otherwise, because
   * logic java 21 preview and other versions are not compatible
   */
  private static boolean isJava21Preview() {
    try {
      return Boolean.parseBoolean(System.getProperty(LAUNCHER_USE_JDK_21_PREVIEW));
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static Method findMethodToRun(Class<?> aClass) {
    boolean java21Preview = isJava21Preview();
    Method methodWithoutArgsCandidate = null;
    // static main methods may be only in this class
    if (java21Preview) {
      for (Method declaredMethod : aClass.getDeclaredMethods()) {
        MainMethodStatus status = getMainMethodStatus(declaredMethod, MainMethodSearchMode.STATIC_METHOD);
        if (status == MainMethodStatus.WithArgs) {
          return declaredMethod;
        }
        else if (status == MainMethodStatus.WithoutArgs && methodWithoutArgsCandidate == null) {
          methodWithoutArgsCandidate = declaredMethod;
        }
      }
    }

    if (methodWithoutArgsCandidate != null) {
      return methodWithoutArgsCandidate;
    }

    Deque<Class<?>> classesToVisit = new ArrayDeque<>();
    classesToVisit.add(aClass);
    Set<Class<?>> visited = new HashSet<>();
    while (!classesToVisit.isEmpty()) {
      Class<?> last = classesToVisit.removeLast();
      Method[] declaredMethods = last.getDeclaredMethods();
      for (Method method : declaredMethods) {
        MainMethodStatus status = getMainMethodStatus(method, java21Preview ? MainMethodSearchMode.NON_STATIC_METHOD : MainMethodSearchMode.ALL_METHOD);
        if (status == MainMethodStatus.WithArgs) {
          return method;
        }
        else if (status == MainMethodStatus.WithoutArgs && methodWithoutArgsCandidate == null) {
          methodWithoutArgsCandidate = method;
        }
      }
      visited.add(aClass);
      Class<?> superclass = last.getSuperclass();
      if (superclass != null) {
        classesToVisit.add(superclass);
      }
      for (Class<?> anInterface : last.getInterfaces()) {
        if (!visited.contains(anInterface)) {
          classesToVisit.add(anInterface);
        }
      }
    }

    return methodWithoutArgsCandidate;
  }

  enum MainMethodStatus {
    NotMain,
    WithArgs,
    WithoutArgs
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
