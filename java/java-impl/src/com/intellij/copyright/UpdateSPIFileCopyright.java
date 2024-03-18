// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright;

import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;

public final class UpdateSPIFileCopyright extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdatePsiFileCopyright(project, module, file, options) {
      @Override
      protected void scanFile() {
        final PsiElement firstChild = getFile().getFirstChild();
        checkComments(firstChild, PsiTreeUtil.skipSiblingsForward(firstChild, PsiComment.class, PsiWhiteSpace.class), true);
      }

      @Override
      protected boolean accept() {
        return getFile().getLanguage() == SPILanguage.INSTANCE;
      }
    };
  }
}
