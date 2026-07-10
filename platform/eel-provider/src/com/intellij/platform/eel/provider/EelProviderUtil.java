// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider;

import com.intellij.openapi.project.Project;
import com.intellij.platform.eel.EelApi;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelMachine;
import com.intellij.platform.eel.EelOsFamily;
import com.intellij.platform.eel.LocalEelApi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Java facade for EEL provider utility functions defined across multiple Kotlin files.
 * Kotlin callers should use the extension functions directly.
 */
@ApiStatus.Experimental
public final class EelProviderUtil {
  private EelProviderUtil() { }

  public static @NotNull EelDescriptor getEelDescriptor(@NotNull Path path) {
    return EelPathDescriptorKt.getEelDescriptor(path);
  }

  public static @NotNull EelOsFamily getOsFamily(@NotNull Path path) {
    return EelPathDescriptorKt.getOsFamily(path);
  }

  public static @NotNull EelMachine getEelMachine(@NotNull Project project) {
    return EelProviderProjectUtilKt.getEelMachine(project);
  }

  public static @NotNull EelDescriptor getEelDescriptor(@NotNull Project project) {
    return EelProviderProjectUtilKt.getEelDescriptor(project);
  }

  public static boolean ownsPath(@NotNull EelMachine machine, @NotNull Path path) {
    return EelPathDescriptorKt.ownsPath(machine, path);
  }

  public static @NotNull EelApi toEelApiBlocking(@NotNull EelDescriptor descriptor) {
    return EelProviderProjectUtilKt.toEelApiBlocking(descriptor);
  }

  public static @NotNull EelApi toEelApiBlocking(@NotNull EelMachine machine, @NotNull EelDescriptor descriptor) {
    return EelProviderProjectUtilKt.toEelApiBlocking(machine, descriptor);
  }

  public static @NotNull LocalEelApi getLocalEel() {
    return LocalEelApiKt.getLocalEel();
  }
}
