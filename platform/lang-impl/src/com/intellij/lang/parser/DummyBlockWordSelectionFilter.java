// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.parser;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.DummyBlockType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public final class DummyBlockWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    return !(element instanceof DummyBlockType.DummyBlock);
  }
}
