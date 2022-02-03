// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class JBAnimatorHelper {

  private static final String PROPERTY_NAME = "WIN_MM_LIB_HIGH_PRECISION_TIMER";
  private static final boolean DEFAULT_VALUE = ApplicationManager.getApplication().isInternal() && SystemInfoRt.isWindows;
  private static final int PERIOD = 1;

  private final @NotNull Set<JBAnimator> requestors;
  private final @NotNull WinMM lib;

  public static void requestHighPrecisionTimer(@NotNull JBAnimator requestor) {
    if (isAvailable()) {
      var helper = JBAnimatorHelperHolder.INSTANCE;
      if (helper.requestors.add(requestor)) {
        helper.lib.timeBeginPeriod(PERIOD);
      }
    }
  }

  public static void cancelHighPrecisionTimer(@NotNull JBAnimator requestor) {
    if (isAvailable()) {
      var helper = JBAnimatorHelperHolder.INSTANCE;
      if (helper.requestors.remove(requestor)) {
        helper.lib.timeEndPeriod(PERIOD);
      }
    }
  }

  public static boolean isAvailable() {
    if (!SystemInfoRt.isWindows) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME, DEFAULT_VALUE);
  }

  public static void setAvailable(boolean value) {
    if (!SystemInfoRt.isWindows) {
      throw new IllegalArgumentException("This option can be set only on Windows");
    }
    PropertiesComponent.getInstance().setValue(PROPERTY_NAME, value, DEFAULT_VALUE);
    var helper = JBAnimatorHelperHolder.INSTANCE;
    if (!helper.requestors.isEmpty()) {
      helper.requestors.clear();
      helper.lib.timeEndPeriod(PERIOD);
    }
  }

  private interface WinMM extends StdCallLibrary {
    int timeBeginPeriod(int period);
    int timeEndPeriod(int period);
  }

  private static class JBAnimatorHelperHolder {
    private static final JBAnimatorHelper INSTANCE = new JBAnimatorHelper();
  }

  private JBAnimatorHelper() {
    requestors = ConcurrentHashMap.newKeySet();
    WinMM library = null;
    if (SystemInfoRt.isWindows) try {
      library = Native.load("winmm", WinMM.class);
    } catch (UnsatisfiedLinkError e) {
      Logger.getInstance(getClass()).error(new RuntimeException("Cannot load 'winmm.dll' library"));
    } catch (Throwable t) {
      Logger.getInstance(getClass()).error(t);
    }
    lib = library != null ? library : new WinMM() {
      @Override
      public int timeBeginPeriod(int period) { return 0; }

      @Override
      public int timeEndPeriod(int period) { return 0; }
    };
  }
}