// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;

/**
 * Provides access to the {@code Application}.
 */
public class ApplicationManager {
  protected static Application ourApplication;
  private static String ourPid;

  private static String getCurrentProcessId() {
    try {
      String name = ManagementFactory.getRuntimeMXBean().getName();
      return name.split("@")[0];
    }
    catch (Exception e) {
      return "-1";
    }
  }

  /**
   * Gets Application.
   *
   * @return {@code Application}
   */
  public static Application getApplication() {
    return ourApplication;
  }

  private static void setApplication(@NotNull Application instance) {
    ourApplication = instance;
    CachedSingletonsRegistry.cleanupCachedFields();
  }

  public static void setApplication(@NotNull Application instance, @NotNull Disposable parent) {
    final Application old = ourApplication;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        if (old != null) { // to prevent NPEs in threads still running
          setApplication(old);
        }
      }
    });
    setApplication(instance);
  }

  public static void setApplication(@NotNull Application instance,
                                    @NotNull Getter<FileTypeRegistry> fileTypeRegistryGetter,
                                    @NotNull Disposable parent) {
    final Application old = ourApplication;
    final Getter<FileTypeRegistry> oldFileTypeRegistry = FileTypeRegistry.ourInstanceGetter;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        if (old != null) { // to prevent NPEs in threads still running
          setApplication(old);
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          FileTypeRegistry.ourInstanceGetter = oldFileTypeRegistry;
        }
      }
    });
    setApplication(instance);
    FileTypeRegistry.ourInstanceGetter = fileTypeRegistryGetter;
  }

  public static String getApplicationPid() {
    if (ourPid == null) {
      ourPid = getCurrentProcessId();
    }

    return String.valueOf(ourPid);
  }
}
