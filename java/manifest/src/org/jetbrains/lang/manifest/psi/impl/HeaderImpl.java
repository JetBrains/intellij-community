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
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.*;

import java.util.List;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class HeaderImpl extends ASTWrapperPsiElement implements Header {
  public HeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String getName() {
    return getNameElement().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    ((LeafElement)getNameElement().getNode()).replaceWithText(name);
    return this;
  }

  @NotNull
  @Override
  public ManifestToken getNameElement() {
    ManifestToken token = (ManifestToken)getNode().findChildByType(ManifestTokenType.HEADER_NAME);
    assert token != null : getText();
    return token;
  }

  @Nullable
  @Override
  public HeaderValue getHeaderValue() {
    return PsiTreeUtil.getChildOfType(this, HeaderValue.class);
  }

  @NotNull
  @Override
  public List<HeaderValue> getHeaderValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HeaderValue.class);
  }

  @Override
  public String toString() {
    return "Header:" + getName();
  }
}
