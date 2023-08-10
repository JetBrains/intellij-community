// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class IfCanBeSwitchActionOnFile extends InvokeIntentionAtElement {

  public IfCanBeSwitchActionOnFile(@NotNull PsiFile file) {
    super(file, new IfCanBeSwitchPolicy(), PsiIfStatement.class, Function.identity());
  }

  private static final class IfCanBeSwitchPolicy extends JavaIntentionPolicy {
    @Override
    protected boolean shouldSkipIntention(@NotNull String actionText) {
      return !actionText.equals(IfCanBeSwitchInspection.getReplaceWithSwitchFixName());
    }
  }
}
