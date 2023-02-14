// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CheckerQualExternalLibraryResolver extends ExternalLibraryResolver {

  public static final ExternalLibraryDescriptor CHECKER_QUAL =
    new ExternalLibraryDescriptor("org.checkerframework", "checker-qual", "3.19.0", "3.19.0");

  private static final Set<String> CHECKER_ANNOS = ContainerUtil.set("Untainted", "Tainted", "PolyTainted");

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName,
                                                 @NotNull ThreeState isAnnotation,
                                                 @NotNull Module contextModule) {
    if (isAnnotation == ThreeState.YES && CHECKER_ANNOS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.checkerframework.checker.tainting.qual." + shortClassName, CHECKER_QUAL);
    }
    return null;
  }
}
