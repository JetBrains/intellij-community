// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jna;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.Native;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Loads JNA once. JNA reads {@code jna.boot.library.path}, {@code jna.nosys} and {@code jna.noclasspath} once, in the
 * static initializer of {@link Native}, and the first JNA user runs it. Code that clears or changes a {@code jna.*}
 * property calls {@link #load()} first, so the JNA of the process keeps the values of the launcher (IJPL-256293).
 * <p>
 * A plugin calls {@link #isLoaded()}. It loads JNA on the first call and reports the result.
 */
public final class JnaLoader {
  private static Boolean ourJnaLoaded = null;

  /**
   * Loads JNA. The platform calls it at startup. A plugin calls {@link #isLoaded()} instead.
   */
  @ApiStatus.Internal
  public static synchronized void load() {
    if (ourJnaLoaded == null) {
      ourJnaLoaded = Boolean.FALSE;

      java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JnaLoader.class.getName());
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

  /**
   * @deprecated use {@link #isLoaded()}; the logger is ignored
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void load(@SuppressWarnings("unused") @NotNull Logger logger) {
    load();
  }

  public static synchronized boolean isLoaded() {
    if (ourJnaLoaded == null) {
      load();
    }
    return ourJnaLoaded;
  }
}
