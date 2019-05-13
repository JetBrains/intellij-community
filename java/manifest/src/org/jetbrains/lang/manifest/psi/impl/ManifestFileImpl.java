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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.ManifestFileTypeFactory;
import org.jetbrains.lang.manifest.ManifestLanguage;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.ManifestFile;
import org.jetbrains.lang.manifest.psi.Section;

import java.util.List;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestFileImpl extends PsiFileBase implements ManifestFile {
  public ManifestFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, ManifestLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ManifestFileTypeFactory.MANIFEST;
  }

  @NotNull
  @Override
  public List<Section> getSections() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Section.class);
  }

  @Nullable
  @Override
  public Section getMainSection() {
    return findChildByClass(Section.class);
  }

  @NotNull
  @Override
  public List<Header> getHeaders() {
    return PsiTreeUtil.getChildrenOfTypeAsList(getFirstChild(), Header.class);
  }

  @Nullable
  @Override
  public Header getHeader(@NotNull String name) {
    Header child = PsiTreeUtil.findChildOfType(getFirstChild(), Header.class);
    while (child != null) {
      if (name.equals(child.getName())) {
        return child;
      }
      child = PsiTreeUtil.getNextSiblingOfType(child, Header.class);
    }
    return null;
  }

  @Override
  public String toString() {
    return "ManifestFile:" + getName();
  }
}
