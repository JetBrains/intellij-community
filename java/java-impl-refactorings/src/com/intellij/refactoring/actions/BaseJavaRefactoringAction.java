// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;

public abstract class BaseJavaRefactoringAction extends BaseRefactoringAction {
  @Override
  public boolean isAvailableForLanguage(Language language) {
    return language.equals(JavaLanguage.INSTANCE);
  }
}
