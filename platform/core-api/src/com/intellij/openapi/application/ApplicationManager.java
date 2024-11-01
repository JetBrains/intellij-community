// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Provides access to the {@link Application}.
 */
public class ApplicationManager {
  @ApiStatus.Internal
  protected volatile static Application ourApplication;

  public static Application getApplication() {
    return ourApplication;
  }

  @ApiStatus.Internal
  public static void setApplication(@Nullable Application instance) {
    ourApplication = instance;
    for (Runnable cleaner : cleaners) {
      cleaner.run();
    }
  }

  public static void setApplication(@NotNull Application instance, @NotNull Disposable parent) {
    Application old = ourApplication;
    Disposer.register(parent, () -> {
      if (old != null) { // to prevent NPEs in threads still running
        setApplication(old);
      }
    });
    setApplication(instance);
  }

  public static void setApplication(
    @NotNull Application instance,
    @NotNull Supplier<? extends FileTypeRegistry> fileTypeRegistryGetter,
    @NotNull Disposable parent
  ) {
    Application old = ourApplication;
    setApplication(instance);
    FileTypeRegistry.setInstanceSupplier(fileTypeRegistryGetter, parent);
    Disposer.register(parent, () -> {
      if (old != null) {
        // to prevent NPEs in threads still running
        setApplication(old);
      }
    });
  }

  private static final List<Runnable> cleaners = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Registers a cleaning operation to be run when the application instance is reset (for example, in tests).
   */
  @ApiStatus.Internal
  public static void registerCleaner(Runnable cleaner) {
    cleaners.add(cleaner);
  }
}
