// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@ApiStatus.Internal
public final class JBAnimatorHelper {

  private static final String PROPERTY_NAME = "WIN_MM_LIB_HIGH_PRECISION_TIMER";
  private static final boolean DEFAULT_VALUE = ApplicationManager.getApplication().isInternal() && SystemInfoRt.isWindows;
  private static final int PERIOD = 1;

  private final @NotNull Set<JBAnimator> requestors;
  private final @NotNull WinMM lib;

  private static @Nullable Throwable exceptionInInitialization = null;

  private static JBAnimatorHelper getInstance() {
    return JBAnimatorHelperHolder.INSTANCE;
  }

  /**
   * Used internally only, do not call it until it's really necessary.
   */
  @ApiStatus.Experimental
  public static void requestHighPrecisionTimer(@NotNull JBAnimator requestor) {
    if (isAvailable()) {
      getInstance().request(requestor);
    }
  }

  /**
   * Used internally only, do not call it until it's really necessary.
   */
  @ApiStatus.Experimental
  public static void cancelHighPrecisionTimer(@NotNull JBAnimator requestor) {
    if (isAvailable()) {
      getInstance().cancel(requestor);
    }
  }

  public static boolean isAvailable() {
    if (!SystemInfoRt.isWindows || exceptionInInitialization != null) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME, DEFAULT_VALUE);
  }

  public static void setAvailable(boolean value) {
    if (exceptionInInitialization != null) {
      Logger.getInstance(JBAnimatorHelper.class).error(exceptionInInitialization);
    }
    if (!SystemInfoRt.isWindows) {
      throw new IllegalArgumentException("This option can be set only on Windows");
    }
    PropertiesComponent.getInstance().setValue(PROPERTY_NAME, value, DEFAULT_VALUE);
    getInstance().reset();
  }

  interface WinMM {
    int timeBeginPeriod(int period);

    int timeEndPeriod(int period);
  }

  private static class JBAnimatorHelperHolder {
    private static final JBAnimatorHelper INSTANCE = new JBAnimatorHelper();
  }

  private JBAnimatorHelper() {
    this(loadWinMM());
  }

  private static @NotNull WinMM loadWinMM() {
    return loadWinMM(SystemInfoRt.isWindows, JBAnimatorHelper::createWinMM, failure -> exceptionInInitialization = failure);
  }

  JBAnimatorHelper(@NotNull WinMM library) {
    requestors = ConcurrentHashMap.newKeySet();
    lib = library;
  }

  void request(@NotNull JBAnimator requestor) {
    if (requestors.add(requestor)) {
      lib.timeBeginPeriod(PERIOD);
    }
  }

  void cancel(@NotNull JBAnimator requestor) {
    if (requestors.remove(requestor)) {
      lib.timeEndPeriod(PERIOD);
    }
  }

  void reset() {
    if (!requestors.isEmpty()) {
      requestors.clear();
      lib.timeEndPeriod(PERIOD);
    }
  }

  static @NotNull WinMM loadWinMM(boolean windows, @NotNull Supplier<? extends WinMM> loader, @NotNull Consumer<Throwable> failureHandler) {
    try {
      if (windows) {
        return loader.get();
      }
    }
    catch (UnsatisfiedLinkError failure) {
      failureHandler.accept(new RuntimeException("Cannot load 'winmm.dll' library"));
    }
    catch (Throwable failure) {
      failureHandler.accept(new RuntimeException("Cannot load 'winmm.dll' library", failure));
    }
    return new WinMM() {
      @Override
      public int timeBeginPeriod(int period) { return 0; }

      @Override
      public int timeEndPeriod(int period) { return 0; }
    };
  }

  static @NotNull WinMM createWinMM() {
    var library = WindowsSystemLibraries.lookup("winmm.dll");
    var linker = Linker.nativeLinker();
    var descriptor = FunctionDescriptor.of(JAVA_INT, JAVA_INT);
    return new FfmWinMM(linker.downcallHandle(library.findOrThrow("timeBeginPeriod"), descriptor),
                       linker.downcallHandle(library.findOrThrow("timeEndPeriod"), descriptor));
  }

  private record FfmWinMM(MethodHandle beginPeriod, MethodHandle endPeriod) implements WinMM {
    @Override
    public int timeBeginPeriod(int period) {
      return invoke(beginPeriod, period);
    }

    @Override
    public int timeEndPeriod(int period) {
      return invoke(endPeriod, period);
    }

    private static int invoke(MethodHandle handle, int period) {
      try {
        return (int)handle.invokeExact(period);
      }
      catch (RuntimeException | Error failure) {
        throw failure;
      }
      catch (Throwable failure) {
        throw new IllegalStateException(failure);
      }
    }
  }
}
