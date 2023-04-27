// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Provides access to the {@link Application}.
 */
public class ApplicationManager {
  protected static Application ourApplication;

  public static Application getApplication() {
    return ourApplication;
  }

  @ApiStatus.Internal
  public static void setApplication(@Nullable Application instance) {
    ourApplication = instance;
    CachedSingletonsRegistry.cleanupCachedFields();
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

  public static void setApplication(@NotNull Application instance,
                                    @NotNull Supplier<? extends FileTypeRegistry> fileTypeRegistryGetter,
                                    @NotNull Disposable parent) {
    Application old = ourApplication;
    setApplication(instance);
    Supplier<? extends FileTypeRegistry> oldFileTypeRegistry = FileTypeRegistry.setInstanceSupplier(fileTypeRegistryGetter);
    Disposer.register(parent, () -> {
      if (old != null) {
        // to prevent NPEs in threads still running
        setApplication(old);
        FileTypeRegistry.setInstanceSupplier(oldFileTypeRegistry);
      }
    });
  }
}
