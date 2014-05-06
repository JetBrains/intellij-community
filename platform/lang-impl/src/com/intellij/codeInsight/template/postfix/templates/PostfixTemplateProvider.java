/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;


import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface PostfixTemplateProvider {

  /**
   * Return all templates registered in the provider
   */
  @NotNull
  Set<PostfixTemplate> getTemplates();

  /**
   * Check symbol can separate template keys
   */
  boolean isTerminalSymbol(char currentChar);

  /**
   * Prepare original file content for template expanding
   * Return context after transformation
   */
  @NotNull
  PsiElement preExpand(@NotNull Editor editor, @NotNull PsiElement context, int offset, @NotNull String key);

  /**
   * Do some actions with the file content before check applicable.
   * Return copyFile or another copy of file after processing
   */
  @NotNull
  PsiFile preCheck(@NotNull Editor editor, @NotNull PsiFile copyFile, int currentOffset);
}
