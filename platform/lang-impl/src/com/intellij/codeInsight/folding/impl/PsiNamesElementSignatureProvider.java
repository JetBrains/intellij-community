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
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;

/**
 * Performs {@code 'PSI element <-> signature'} mappings on the basis of unique path of {@link PsiNamedElement PSI named elements}
 * to the PSI root.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/7/11 11:58 AM
 */
public class PsiNamesElementSignatureProvider extends AbstractElementSignatureProvider {
  
  private static final String TYPE_MARKER            = "n";
  private static final String TOP_LEVEL_CHILD_MARKER = "!!top";
  private static final String DOC_COMMENT_MARKER     = "!!doc";
  private static final String CODE_BLOCK_MARKER      = "!!block";
  
  @Override
  protected PsiElement restoreBySignatureTokens(@NotNull PsiFile file,
                                                @NotNull PsiElement parent,
                                                @NotNull final String type,
                                                @NotNull StringTokenizer tokenizer)
  {
    if (!TYPE_MARKER.equals(type)) {
      return null;
    }
    String elementMarker = tokenizer.nextToken();
    if (TOP_LEVEL_CHILD_MARKER.equals(elementMarker)) {
      PsiElement[] children = file.getChildren();
      PsiElement result = null;
      for (PsiElement child : children) {
        if (child instanceof PsiWhiteSpace) {
          continue;
        }
        if (result == null) {
          result = child;
        }
        else {
          // More than one top-level non-white space children. Can't match.
          return null;
        }
      }
      return result;
    }
    else if (DOC_COMMENT_MARKER.equals(elementMarker)) {
      PsiElement candidate = parent.getFirstChild();
      return candidate instanceof PsiComment ? candidate : null; 
    }
    else if (CODE_BLOCK_MARKER.equals(elementMarker)) {
      for (PsiElement child : parent.getChildren()) {
        PsiElement firstChild = child.getFirstChild();
        PsiElement lastChild = child.getLastChild();
        if (firstChild != null && lastChild != null && "{".equals(firstChild.getText()) && "}".equals(lastChild.getText())) {
          return child;
        } 
      }
      return null;
    }

    try {
      int index = Integer.parseInt(tokenizer.nextToken());
      return restoreElementInternal(parent, elementMarker, index, PsiNamedElement.class);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public String getSignature(@NotNull final PsiElement element) {
    StringBuilder buffer = null;
    int length;
    for (PsiElement current = element; current != null && !(current instanceof PsiFile); current = current.getParent()) {
      length = buffer == null ? 0 : buffer.length();
      StringBuilder b = getSignature(current, buffer);
      if (b == null && buffer != null && current.getParent() instanceof PsiFile) {
        buffer.append(TYPE_MARKER).append(ELEMENT_TOKENS_SEPARATOR).append(TOP_LEVEL_CHILD_MARKER).append(ELEMENTS_SEPARATOR);
        break;
      } 
      buffer = b;
      if (buffer == null || length >= buffer.length()) {
        return null;
      }
      buffer.append(ELEMENTS_SEPARATOR);
    }

    if (buffer == null) {
      return null;
    } 
    
    buffer.setLength(buffer.length() - 1);
    return buffer.toString();
  }

  /**
   * Tries to produce signature for the exact given PSI element.
   * 
   * @param element  target element
   * @param buffer   buffer to store the signature in
   * @return         buffer that contains signature of the given element if it was produced;
   *                 <code>null</code> as an indication that signature for the given element was not produced
   */
  @SuppressWarnings("unchecked")
  @Nullable
  private static StringBuilder getSignature(@NotNull PsiElement element, @Nullable StringBuilder buffer) {
    if (element instanceof PsiNamedElement) {
      PsiNamedElement named = (PsiNamedElement)element;
      int index = getChildIndex(named, element.getParent(), named.getName(), (Class<PsiNamedElement>)named.getClass());
      StringBuilder bufferToUse = buffer;
      if (bufferToUse == null) {
        bufferToUse = new StringBuilder();
      }
      bufferToUse.append(TYPE_MARKER).append(ELEMENT_TOKENS_SEPARATOR).append(named.getName())
        .append(ELEMENT_TOKENS_SEPARATOR).append(index);
      return bufferToUse;
    }
    else if (element instanceof PsiComment) {
      PsiElement parent = element.getParent();
      boolean nestedComment = false;
      if (parent instanceof PsiComment && parent.getTextRange().equals(element.getTextRange())) {
        parent = parent.getParent();
        nestedComment = true;
      }
      if (parent instanceof PsiNamedElement && (nestedComment || parent.getFirstChild() == element)) {
        // Consider doc comment element that is the first child of named element to be a doc comment.
        StringBuilder bufferToUse = buffer;
        if (bufferToUse == null) {
          bufferToUse = new StringBuilder();
        }
        bufferToUse.append(TYPE_MARKER).append(ELEMENT_TOKENS_SEPARATOR).append(DOC_COMMENT_MARKER);
        return bufferToUse;
      } 
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiNamedElement && !(parent instanceof PsiFile)) {
      PsiElement firstChild = element.getFirstChild();
      PsiElement lastChild = element.getLastChild();
      if (firstChild != null && "{".equals(firstChild.getText()) && lastChild != null && "}".equals(lastChild.getText())) {
        StringBuilder bufferToUse = buffer;
        if (bufferToUse == null) {
          bufferToUse = new StringBuilder();
        }
        bufferToUse.append(TYPE_MARKER).append(ELEMENT_TOKENS_SEPARATOR).append(CODE_BLOCK_MARKER);
        return bufferToUse;
      }
    } 
     
    return null;
  }
}
