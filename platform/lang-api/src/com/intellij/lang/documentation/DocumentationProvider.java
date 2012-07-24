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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see com.intellij.lang.LanguageDocumentation
 * @see AbstractDocumentationProvider
 */
public interface DocumentationProvider {

  /**
   * Please use {@link com.intellij.lang.LanguageDocumentation} instead of this for language-specific documentation
   */
  ExtensionPointName<DocumentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.documentationProvider");

  @Nullable
  String getQuickNavigateInfo(PsiElement element, PsiElement originalElement);

  @Nullable
  List<String> getUrlFor(PsiElement element, PsiElement originalElement);

  /**
   * Callback for asking the doc provider for the complete documentation.
   * <p/>
   * Underlying implementation may be time-consuming, that's why this method is expected not to be called from EDT.
   *  
   * @param element          target element which documentation is being requested
   * @param originalElement  element initially picked up from the current context
   * @return                 target element's documentation (if any)
   */
  @Nullable
  String generateDoc(PsiElement element, PsiElement originalElement);

  @Nullable
  PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element);

  @Nullable
  PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context);
}
