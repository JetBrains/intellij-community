// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Contributes content to the following IDE features:
 * <ul>
 *   <li>Quick Documentation (invoked via explicit action or shown on mouse hover)</li>
 *   <li>Navigation info (shown in editor on Ctrl/Cmd+mouse hover)</li>
 *   <li>Rendered representation of documentation comments</li>
 * </ul>
 * <p>
 * Extend {@link AbstractDocumentationProvider}.
 * <p>
 * Language-specific instance should be registered in {@code com.intellij.lang.documentationProvider} extension point; otherwise use
 * {@code com.intellij.documentationProvider}.
 * </p>
 *
 * @see com.intellij.lang.LanguageDocumentation
 * @see DocumentationProviderEx
 * @see ExternalDocumentationProvider
 * @see ExternalDocumentationHandler
 */
public interface DocumentationProvider {

  /**
   * Please use {@code com.intellij.lang.documentationProvider} instead of this for language-specific documentation.
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
  default @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
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
   * {@link ExternalDocumentationProvider#fetchExternalDocumentation(Project, PsiElement, List, boolean)} will be used.
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
  default @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    return null;
  }

  /**
   * Same as {@link #generateDoc(PsiElement, PsiElement)}, but used for documentation showed on mouse hover in editor.
   * <p>
   * At the moment it's only invoked to get initial on-hover documentation. If user navigates any link in that documentation,
   * {@link #generateDoc(PsiElement, PsiElement)} will be used to fetch corresponding content.
   */
  @Nullable
  default @Nls String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    return generateDoc(element, originalElement);
  }

  /**
   * This is used to display rendered documentation in editor, in place of corresponding documentation comment's text.
   *
   * @see #collectDocComments(PsiFile, Consumer)
   */
  default @Nls @Nullable String generateRenderedDoc(@NotNull PsiDocCommentBase comment) {
    return null;
  }

  /**
   * This defines documentation comments in file, which can be rendered in place. HTML content to be displayed will be obtained using
   * {@link #generateRenderedDoc(PsiDocCommentBase)} method.
   * <p>
   * To support cases, when rendered fragment doesn't have representing {@code PsiDocCommentBase} element (e.g. for the sequence of line
   * comments in languages not having a block comment concept), fake elements (not existing in the {@code file}) might be returned. In such
   * a case, {@link #findDocComment(PsiFile, TextRange)} should also be implemented by the documentation provider, for the rendered
   * documentation view to work correctly.
   */
  default void collectDocComments(@NotNull PsiFile file, @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) { }

  /**
   * This method is needed to support rendered representation of documentation comments in editor. It should return doc comment located at
   * the provided text range in a file. Overriding the default implementation only makes sense for languages which use fake
   * {@code PsiDocCommentBase} implementations (e.g. in cases when rendered view is provided for a set of adjacent line comments, and
   * there's no real {@code PsiDocCommentBase} element in a file representing the range to render).
   *
   * @see #collectDocComments(PsiFile, Consumer)
   */
  default @Nullable PsiDocCommentBase findDocComment(@NotNull PsiFile file, @NotNull TextRange range) {
    PsiDocCommentBase comment = PsiTreeUtil.getParentOfType(file.findElementAt(range.getStartOffset()), PsiDocCommentBase.class, false);
    return comment == null || !range.equals(comment.getTextRange()) ? null : comment;
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

  /**
   * Override this method if standard platform's choice for target PSI element to show documentation for (element either declared or
   * referenced at target offset) isn't suitable for your language. For example, it could be a keyword where there's no
   * {@link com.intellij.psi.PsiReference}, but for which users might benefit from context help.
   *
   * @param targetOffset   equals to caret offset for 'Quick Documentation' action, and to offset under mouse cursor for documentation shown
   *                       on mouse hover
   * @param contextElement the leaf PSI element in {@code file} at target offset
   * @return target PSI element to show documentation for, or {@code null} if it should be determined by standard platform's logic (default
   * behaviour)
   */
  @Nullable
  default PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                   @NotNull final PsiFile file,
                                                   @Nullable PsiElement contextElement,
                                                   int targetOffset) {
    //noinspection deprecation
    return (this instanceof DocumentationProviderEx)
           ? ((DocumentationProviderEx)this).getCustomDocumentationElement(editor, file, contextElement)
           : null;
  }
}