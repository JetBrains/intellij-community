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
   * Prepare file for template expanding. Running on EDT.
   * E.g. java postfix templates adds semicolon after caret in order to simplify context checking.
   *
   * File content doesn't contain template's key, it is deleted just before this method invocation.
   *
   * Note that while postfix template is checking its availability the file parameter is a _COPY_ of the real file,
   * so you can do with it anything that you want, but in the same time it doesn't recommended to modify editor state because it's real.
   */
  void preExpand(@NotNull PsiFile file, @NotNull Editor editor);

  /**
   * Invoked after template finished (doesn't matter if it finished successfully or not).
   * E.g. java postfix template use this method for deleting inserted semicolon.
   */
  void afterExpand(@NotNull PsiFile file, @NotNull Editor editor);

  /**
   * Prepare file for checking availability of templates.
   * Almost the same as {@link this#preExpand(com.intellij.psi.PsiFile, com.intellij.openapi.editor.Editor)} with several differences:
   * 1. Processes copy of file. So implementations can modify it without corrupting the real file.
   * 2. Could be invoked from anywhere (EDT, write-action, read-action, completion-thread etc.). So implementations should make 
   * additional effort to make changes in file.
   *
   * Content of file copy doesn't contain template's key, it is deleted just before this method invocation.
   * 
   * NOTE: editor is real (not copy) and it doesn't represents the copyFile. 
   * So it's safer to use currentOffset parameter instead of offset from editor. Do not modify text via editor.
   */
  @NotNull
  PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset);
}
