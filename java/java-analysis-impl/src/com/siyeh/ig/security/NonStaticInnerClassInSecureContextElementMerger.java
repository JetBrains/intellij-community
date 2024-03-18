// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.security;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Bas Leijdekkers
 */
public final class NonStaticInnerClassInSecureContextElementMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "PrivateMemberAccessBetweenOuterAndInnerClass";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[] {"NonStaticInnerClassInSecureContext", "PrivateMemberAccessBetweenOuterAndInnerClass"};
  }

  @Override
  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return false;
  }

  @Override
  protected boolean writeMergedContent(@NotNull Element toolElement) {
    return true;
  }
}
