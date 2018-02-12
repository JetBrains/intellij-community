/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base super-class for {@link LineWrapPositionStrategy} implementations that want to restrict wrap positions
 * only for particular elements/tokens (e.g. we may want to avoid line wrap in the middle of xml tag name etc).
 * 
 * @author Denis Zhdanov
 * @since 5/12/11 12:30 PM
 */
public abstract class PsiAwareLineWrapPositionStrategy implements LineWrapPositionStrategy {

  private static final Logger LOG = Logger.getInstance(PsiAwareLineWrapPositionStrategy.class);
  
  private final TokenSet myEnabledTypes;
  private final boolean  myNonVirtualOnly;

  /**
   * Creates new {@code PsiAwareLineWrapPositionStrategy} object.
   * 
   * @param nonVirtualOnly  defines if current PSI-aware logic should be exploited only for 'real wrap' position requests
   * @param enabledTypes    target element/token types where line wrapping is allowed
   */
  public PsiAwareLineWrapPositionStrategy(boolean nonVirtualOnly, @NotNull IElementType ... enabledTypes) {
    myEnabledTypes = TokenSet.create(enabledTypes);
    myNonVirtualOnly = nonVirtualOnly;
    if (enabledTypes.length <= 0) {
      LOG.warn(String.format("%s instance is created with empty token/element types. That will lead to inability to perform line wrap",
                             getClass().getName()));
    }
  }

  @Override
  public int calculateWrapPosition(@NotNull Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean isSoftWrap) {
    if (isSoftWrap && myNonVirtualOnly) {
      LineWrapPositionStrategy implementation = LanguageLineWrapPositionStrategy.INSTANCE.getDefaultImplementation();
      return implementation.calculateWrapPosition(
        document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, isSoftWrap
      );
    }

    if (project == null) {
      return -1;
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager == null) {
      return -1;
    }

    PsiFile psiFile = documentManager.getPsiFile(document);
    if (psiFile == null) {
      return -1;
    }

    documentManager.commitDocument(document);
    PsiElement element = psiFile.findElementAt(maxPreferredOffset);
    if (element == null) {
      return -1;
    }

    for (; element != null && element.getTextRange().getEndOffset() > startOffset; element = getPrevious(element)) {
      if (allowToWrapInside(element)) {
        TextRange textRange = element.getTextRange();
        int start = Math.max(textRange.getStartOffset(), startOffset);
        int end = Math.min(textRange.getEndOffset(), endOffset);
        int result = doCalculateWrapPosition(document, project, start, end, maxPreferredOffset, false, isSoftWrap);
        if (result >= 0) {
          return result;
        }

        // Assume that it's possible to wrap on token boundary (makes sense at least for the tokens that occupy one symbol only).
        if (end <= maxPreferredOffset) {
          return end;
        }

        if (start > startOffset) {
          return start;
        }
      }
    }
    return -1;
  }

  /**
   * Serves for the same purposes as {@link #calculateWrapPosition(Document, Project, int, int, int, boolean, boolean)} but ensures
   * that given offsets target {@link #PsiAwareLineWrapPositionStrategy(boolean, IElementType...) enabled token/element types}.
   * 
   * @param document                          target document which text is being processed
   * @param project                           target project
   * @param startOffset                       start offset to use with the given text holder (inclusive)
   * @param endOffset                         end offset to use with the given text holder (exclusive)
   * @param maxPreferredOffset                this method is expected to do its best to return offset that belongs to
   *                                          {@code (startOffset; maxPreferredOffset]} interval. However, it's allowed
   *                                          to return value from {@code (maxPreferredOffset; endOffset]} interval
   *                                          unless {@code 'allowToBeyondMaxPreferredOffset'} if {@code 'false'}
   * @param allowToBeyondMaxPreferredOffset   indicates if it's allowed to return value from
   *                                          {@code (maxPreferredOffset; endOffset]} interval in case of inability to
   *                                          find appropriate offset from {@code (startOffset; maxPreferredOffset]} interval
   * @param virtual                           identifies if current request is for virtual wrap (soft wrap) position
   * @return                                  offset from {@code (startOffset; endOffset]} interval where
   *                                          target line should be wrapped OR {@code -1} if no wrapping should be performed
   */
  protected abstract int doCalculateWrapPosition(
    @NotNull Document document, @Nullable Project project, int startOffset, int endOffset, int maxPreferredOffset,
    boolean allowToBeyondMaxPreferredOffset, boolean virtual
  );

  /**
   * Allows to check if line wrap at the text range defined by the given element is allowed.
   * 
   * @param element     element that defines target text range
   * @return            {@code true} if wrapping at the text range defined by the given element is allowed;
   *                    {@code false} otherwise
   */
  private boolean allowToWrapInside(@NotNull PsiElement element) {
    TextRange textRange = element.getTextRange();
    if (textRange == null) {
      return false;
    } 
    for (PsiElement parent = element; parent != null && textRange.equals(parent.getTextRange()); parent = parent.getParent()) {
      ASTNode parentNode = parent.getNode();
      if (parentNode != null && myEnabledTypes.contains(parentNode.getElementType())) {
        return true;
      }
    }
    return false;
  }
  
  @Nullable
  private static PsiElement getPrevious(@NotNull PsiElement element) {
    PsiElement result = element.getPrevSibling();
    if (result != null) {
      return result;
    } 
    
    PsiElement parent = element.getParent();
    if (parent == null) {
      return null;
    }

    PsiElement parentSibling = null;
    for (; parent != null && parentSibling == null; parent = parent.getParent()) {
      parentSibling = parent.getPrevSibling();
    }

    if (parentSibling == null) {
      return null;
    }

    result = parentSibling.getLastChild();
    return result == null ? parentSibling : result;
  }
}
