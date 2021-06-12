// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface ExpressionContext {
  @NonNls Key<String> SELECTION = Key.create("SELECTION");

  Project getProject();

  @Nullable
  Editor getEditor();
  int getStartOffset();
  int getTemplateStartOffset();
  int getTemplateEndOffset();
  <T> T getProperty(Key<T> key);
  @Nullable
  PsiElement getPsiElementAtStartOffset();
  @Nullable
  TextResult getVariableValue(String variableName);
}

