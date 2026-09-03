// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jna;

import com.sun.jna.Native;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class JnaLoader {
  private static Boolean ourJnaLoaded = null;

  public static synchronized void load() {
    if (ourJnaLoaded == null) {
      ourJnaLoaded = Boolean.FALSE;

      Logger logger = Logger.getLogger(JnaLoader.class.getName());
      String osName = System.getProperty("os.name", "");
      if (osName.startsWith("Windows") && Boolean.getBoolean("ide.native.launcher")) {
        // temporary fix for JNA + `SetDefaultDllDirectories` DLL loading issue (IJPL-157390)
        String winDir = System.getenv("SystemRoot");
        if (winDir != null) {
          String path = System.getProperty("jna.platform.library.path");
          path = (path == null ? "" : path + ';') + winDir + "\\System32";
          System.setProperty("jna.platform.library.path", path);
        }
      }

      try {
        long t = System.currentTimeMillis();
        int ptrSize = Native.POINTER_SIZE;
        t = System.currentTimeMillis() - t;
        logger.info("JNA library (" + (ptrSize << 3) + "-bit) loaded in " + t + " ms");
        ourJnaLoaded = Boolean.TRUE;
      }
      catch (Throwable t) {
        logger.log(
          Level.WARNING,
          "Unable to load JNA library (" + osName + '/' + System.getProperty("os.version") +
          ", jna.boot.library.path=" + System.getProperty("jna.boot.library.path") + ')',
          t);
      }
    }
  }

  public static synchronized boolean isLoaded() {
    if (ourJnaLoaded == null) {
      load();
    }
    return ourJnaLoaded;
  }
}
