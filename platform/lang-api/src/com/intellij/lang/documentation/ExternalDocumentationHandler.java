/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExternalDocumentationHandler {
  boolean handleExternal(PsiElement element, PsiElement originalElement);
  boolean handleExternalLink(PsiManager psiManager, String link, PsiElement context);
  boolean canFetchDocumentationLink(String link);
  
  @NotNull
  String fetchExternalDocumentation(@NotNull String link, @Nullable PsiElement element);

  /**
   * Defines whether we will show external documentation
   * link at the bottom of the documentation pane or not.
   *
   *
   * @return true if external documentation link should be
   * shown, false otherwise
   */
  default boolean canHandleExternal(@Nullable PsiElement element,
                                    @Nullable PsiElement originalElement) {
    return true;
  }
}
