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
package org.jetbrains.lang.manifest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.lang.manifest.ManifestLanguage;
import org.jetbrains.lang.manifest.psi.impl.HeaderImpl;
import org.jetbrains.lang.manifest.psi.impl.HeaderValuePartImpl;
import org.jetbrains.lang.manifest.psi.impl.SectionImpl;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public abstract class ManifestElementType extends IElementType {
  public static final IFileElementType FILE = new IFileElementType("ManifestFile", ManifestLanguage.INSTANCE);

  public static final IElementType SECTION = new ManifestElementType("SECTION") {
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new SectionImpl(node);
    }
  };

  public static final IElementType HEADER = new ManifestElementType("HEADER") {
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new HeaderImpl(node);
    }
  };

  public static final IElementType HEADER_VALUE_PART = new ManifestElementType("HEADER_VALUE_PART") {
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new HeaderValuePartImpl(node);
    }
  };

  public ManifestElementType(String name) {
    super(name, ManifestLanguage.INSTANCE);
  }

  public abstract PsiElement createPsi(ASTNode node);

  @Override
  public String toString() {
    return "MF:" + super.toString();
  }
}
