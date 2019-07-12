// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides documentation for PSI elements.
 * <p>
 * Extend {@link AbstractDocumentationProvider}.
 *
 * @see com.intellij.lang.LanguageDocumentation
 * @see DocumentationProviderEx
 * @see ExternalDocumentationProvider
 * @see ExternalDocumentationHandler
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
   * @return the documentation to show, or {@code null} if the provider can't provide any documentation for this element. Documentation can contain
   * HTML markup. If HTML special characters need to be shown in popup, they should be properly escaped.
   */
  @Nullable
  default String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  /**
   * Returns the list of possible URLs to show as external documentation for the specified element.
   *
   * @param element         the element for which the documentation is requested (for example, if the mouse is over
   *                        a method reference, this will be the method to which the reference is resolved).
   * @param originalElement the element under the mouse cursor
   * @return the list of URLs to open in the browser or to use for showing documentation internally ({@link ExternalDocumentationProvider}).
   * If the list contains a single URL, it will be opened.
   * If the list contains multiple URLs, the user will be prompted to choose one of them.
   * For {@link ExternalDocumentationProvider}, first URL, yielding non-empty result in
   * {@link ExternalDocumentationProvider#fetchExternalDocumentation(Project, PsiElement, List)} will be used.
   */
  @Nullable
  default List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  /**
   * <p>Callback for asking the doc provider for the complete documentation.
   * Underlying implementation may be time-consuming, that's why this method is expected not to be called from EDT.</p>
   *
   * <p>One can use {@link DocumentationMarkup} to get proper content layout. Typical sample will look like this:
   * <pre>
   * DEFINITION_START + definition + DEFINITION_END +
   * CONTENT_START + main description + CONTENT_END +
   * SECTIONS_START +
   *   SECTION_HEADER_START + section name +
   *     SECTION_SEPARATOR + "&lt;p&gt;" + section content + SECTION_END +
   *   ... +
   * SECTIONS_END
   * </pre>
   * </p>
   * To show different content on mouse hover in editor, {@link #generateHoverDoc(PsiElement, PsiElement)} should be implemented.
   *
   * @param element         the element for which the documentation is requested (for example, if the mouse is over
   *                        a method reference, this will be the method to which the reference is resolved).
   * @param originalElement the element under the mouse cursor
   * @return target element's documentation, or {@code null} if provider is unable to generate documentation
   * for the given element
   */
  @Nullable
  default String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    return null;
  }

  /**
   * Same as {@link #generateDoc(PsiElement, PsiElement)}, but used for documentation showed on mouse hover in editor.
   * <p>
   * At the moment it's only invoked to get initial on-hover documentation. If user navigates any link in that documentation,
   * {@link #generateDoc(PsiElement, PsiElement)} will be used to fetch corresponding content.
   */
  @Nullable
  default String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    return generateDoc(element, originalElement);
  }

  @Nullable
  default PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  /**
   * Returns the target element for a link in a documentation comment. The link needs to use the
   * {@link DocumentationManagerProtocol#PSI_ELEMENT_PROTOCOL} protocol.
   *
   * @param psiManager the PSI manager for the project in which the documentation is requested.
   * @param link       the text of the link, not including the protocol.
   * @param context    the element from which the navigation is performed.
   * @return the navigation target, or {@code null} if the link couldn't be resolved.
   * @see DocumentationManagerUtil#createHyperlink(StringBuilder, String, String, boolean)
   */
  @Nullable
  default PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}