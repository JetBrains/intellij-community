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

package com.intellij.lang.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see com.intellij.lang.LanguageDocumentation
 */
public interface DocumentationProvider {

  ExtensionPointName<DocumentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.documentationProvider");

  @Nullable
  String getQuickNavigateInfo(PsiElement element, PsiElement originalElement);

  @Nullable
  List<String> getUrlFor(PsiElement element, PsiElement originalElement);

  @Nullable
  String generateDoc(PsiElement element, PsiElement originalElement);

  @Nullable
  PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element);

  @Nullable
  PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context);
}
