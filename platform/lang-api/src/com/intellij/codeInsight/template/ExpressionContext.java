/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  /**
   * @return all template context types matching the template invocation place
   */
  @NotNull
  TemplateContextType[] getCompatibleContexts();
}

