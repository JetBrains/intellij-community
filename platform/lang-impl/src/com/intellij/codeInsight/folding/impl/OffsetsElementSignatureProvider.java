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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.StringTokenizer;

/**
 * Performs {@code 'PSI element <-> signature'} mappings on the basis of the target PSI element's offsets.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/7/11 11:59 AM
 */
public class OffsetsElementSignatureProvider extends AbstractElementSignatureProvider {
  
  private static final String MARKER = "e";
  
  @Override
  protected PsiElement restoreBySignatureTokens(@NotNull PsiFile file, @NotNull PsiElement parent, String type, StringTokenizer tokenizer) {
    if (!MARKER.equals(type)) {
      return null;
    }
    String name = tokenizer.nextToken();
    StringTokenizer tok1 = new StringTokenizer(name, ":");
    int start = Integer.parseInt(tok1.nextToken());
    int end = Integer.parseInt(tok1.nextToken());
    PsiElement element = file.findElementAt(start);
    if (element == null) {
      return null;
    }
    
    TextRange range = element.getTextRange();
    while (range != null && range.getStartOffset() == start && range.getEndOffset() < end) {
      element = element.getParent();
      range = element.getTextRange();
    }
    if (range != null && range.getStartOffset() == start && range.getEndOffset() == end) {
      return element;
    }
    return null;
  }

  @Override
  public String getSignature(@NotNull PsiElement element) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(MARKER).append("#");
    buffer.append(element.getTextRange().getStartOffset());
    buffer.append(":");
    buffer.append(element.getTextRange().getEndOffset());
    return buffer.toString();
  }
}
