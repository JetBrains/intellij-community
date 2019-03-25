// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.psi.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import org.jetbrains.annotations.NotNull;

public class JsonTreeChangePreprocessor extends PsiTreeChangePreprocessorBase {
  public JsonTreeChangePreprocessor(@NotNull Project project) {
    super(project);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof JsonFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    return element.getLanguage() instanceof JsonLanguage;
  }
}