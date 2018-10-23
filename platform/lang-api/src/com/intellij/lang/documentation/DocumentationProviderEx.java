/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author peter
 */
public class DocumentationProviderEx implements DocumentationProvider {
  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null; 
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null; 
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    return null; 
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null; 
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null; 
  }

  /**
   * @param contextElement the leaf PSI element in {@code file} at the editor's caret offset.
   * @return a PSI element for retrieving documentation, that is neither declared nor referenced at the current editor caret.
   * For example, it could be a keyword where there's no {@link com.intellij.psi.PsiReference}, but for which users might benefit from context help.
   */
  @Nullable
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor, @NotNull final PsiFile file, @Nullable PsiElement contextElement) {
    return null;
  }

  @Nullable
  public Image getLocalImageForElement(@NotNull PsiElement element, @NotNull String imageSpec) {
    return null;
  }
}
