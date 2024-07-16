// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JCiPExternalLibraryResolver extends ExternalLibraryResolver {
  private static final ExternalLibraryDescriptor JDCIP_LIBRARY_DESCRIPTOR =
    new ExternalLibraryDescriptor("net.jcip", "jcip-annotations", null, null, "1.0") {
      @Override
      public @NotNull String getPresentableName() {
        return "jcip-annotations.jar";
      }
    };

  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (JCiPUtil.isJCiPAnnotation(shortClassName) && isAnnotation == ThreeState.YES) {
      return new ExternalClassResolveResult("net.jcip.annotations." + shortClassName, JDCIP_LIBRARY_DESCRIPTOR);
    }
    return null;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("net.jcip.annotations")) {
      return JDCIP_LIBRARY_DESCRIPTOR;
    }
    return null;
  }
}
