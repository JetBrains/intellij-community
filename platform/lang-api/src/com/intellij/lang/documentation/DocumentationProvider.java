/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see com.intellij.lang.LanguageDocumentation
 * @see DocumentationProviderEx
 * @see AbstractDocumentationProvider
 * @see ExternalDocumentationProvider
 */
public interface DocumentationProvider {

  /**
   * Please use {@link com.intellij.lang.LanguageDocumentation} instead of this for language-specific documentation
   */
  ExtensionPointName<DocumentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.documentationProvider");

  /**
   * Returns the text to show in the Ctrl-hover popup for the specified element.
   *
   * @param element         the element for which the documentation is requested (for example, if the mouse is over
   *                        a method reference, this will be the method to which the reference is resolved).
   * @param originalElement the element under the mouse cursor
   * @return the documentation to show, or null if the provider can't provide any documentation for this element.
   */
  @Nullable
  String getQuickNavigateInfo(PsiElement element, PsiElement originalElement);

  /**
   * Returns the list of possible URLs to show as external documentation for the specified element.
   * @param element         the element for which the documentation is requested (for example, if the mouse is over
   *                        a method reference, this will be the method to which the reference is resolved).
   * @param originalElement the element under the mouse cursor
   * @return the list of URLs to open in the browser or to use for showing documentation internally ({@link ExternalDocumentationProvider}).
   *         If the list contains a single URL, it will be opened.
   *         If the list contains multiple URLs, the user will be prompted to choose one of them.
   *         For {@link ExternalDocumentationProvider}, first URL, yielding non-empty result in
   *         {@link ExternalDocumentationProvider#fetchExternalDocumentation(Project, PsiElement, List)} will be used.
   */
  @Nullable
  List<String> getUrlFor(PsiElement element, PsiElement originalElement);

  /**
   * Callback for asking the doc provider for the complete documentation.
   * <p/>
   * Underlying implementation may be time-consuming, that's why this method is expected not to be called from EDT.
   * <p/>
   * One can use {@link DocumentationMarkup} to get proper content layout. Typical sample will look like this:
   * <pre>
   * DEFINITION_START + definition + DEFINITION_END +
   * CONTENT_START + main description + CONTENT_END +
   * SECTIONS_START +
   *   SECTION_HEADER_START + section name +
   *     SECTION_SEPARATOR + "<p>" + section content + SECTION_END +
   *   ... +
   * SECTIONS_END
   * </pre>
   *
   * @param element         the element for which the documentation is requested (for example, if the mouse is over
   *                        a method reference, this will be the method to which the reference is resolved).
   * @param originalElement the element under the mouse cursor
   * @return                target element's documentation, or {@code null} if provider is unable to generate documentation
   *                        for the given element
   */
  @Nullable
  String generateDoc(PsiElement element, @Nullable PsiElement originalElement);

  @Nullable
  PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element);

  /**
   * Returns the target element for a link in a documentation comment. The link needs to use the
   * {@link DocumentationManagerProtocol#PSI_ELEMENT_PROTOCOL} protocol.
   *
   * @param psiManager the PSI manager for the project in which the documentation is requested.
   * @param link       the text of the link, not including the protocol.
   * @param context    the element from which the navigation is performed.
   * @return the navigation target, or null if the link couldn't be resolved.
   * @see DocumentationManagerUtil#createHyperlink(StringBuilder, String, String, boolean)
   */
  @Nullable
  PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context);
}
