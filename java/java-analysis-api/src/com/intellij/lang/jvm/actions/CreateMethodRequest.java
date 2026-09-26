// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CreateMethodRequest extends CreateExecutableRequest {

  @NotNull
  String getMethodName();

  @NotNull
  List<ExpectedType> getReturnType();

  /**
   * @return should start live template after a new method was created.
   */
  default boolean isStartTemplate() {
    return true;
  }

  /**
   * @return element which should be replaced with method
   */
  default PsiElement getElementToReplace() {
    return null;
  }
}
