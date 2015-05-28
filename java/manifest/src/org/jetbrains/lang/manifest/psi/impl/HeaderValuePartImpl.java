/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;
import org.jetbrains.lang.manifest.header.HeaderParserRepository;
import org.jetbrains.lang.manifest.psi.HeaderValuePart;
import org.jetbrains.lang.manifest.psi.ManifestToken;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class HeaderValuePartImpl extends ASTWrapperPsiElement implements HeaderValuePart {
  private static final TokenSet SPACES = TokenSet.create(ManifestTokenType.SIGNIFICANT_SPACE, ManifestTokenType.NEWLINE);

  private final HeaderParserRepository myRepository;

  public HeaderValuePartImpl(ASTNode node) {
    super(node);
    myRepository = ServiceManager.getService(HeaderParserRepository.class);
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return getUnwrappedText().isEmpty() ? PsiReference.EMPTY_ARRAY : myRepository.getReferences(this);
  }

  @NotNull
  @Override
  public String getUnwrappedText() {
    StringBuilder builder = new StringBuilder();

    for (PsiElement element = getFirstChild(); element != null; element = element.getNextSibling()) {
      if (!(isSpace(element))) {
        builder.append(element.getText());
      }
    }

    return builder.toString().trim();
  }

  @NotNull
  @Override
  public TextRange getHighlightingRange() {
    int endOffset = getTextRange().getEndOffset();
    PsiElement last = getLastChild();
    while (isSpace(last)) {
      endOffset -= last.getTextLength();
      last = last.getPrevSibling();
    }

    int startOffset = getTextOffset();
    PsiElement first = getFirstChild();
    while (startOffset < endOffset && isSpace(first)) {
      startOffset += first.getTextLength();
      first = first.getNextSibling();
    }

    return new TextRange(startOffset, endOffset);
  }

  @Override
  public String toString() {
    return "HeaderValuePart";
  }

  private static boolean isSpace(PsiElement element) {
    return element instanceof ManifestToken && SPACES.contains(((ManifestToken)element).getTokenType());
  }
}
