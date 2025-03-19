// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Utilities related to single-file Java programs (JEP 330)
 */
public final class JavaPsiSingleFileSourceUtil {
  /**
   * @return true if file correspond to the shebang script
   */
  public static boolean isJavaHashBangScript(@NotNull PsiFile containingFile) {
    if (!(containingFile instanceof PsiJavaFile)) return false;
    if (containingFile instanceof PsiFileEx && !((PsiFileEx)containingFile).isContentsLoaded()) {
      VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile.isInLocalFileSystem()) {
        try {
          // don't build PSI when not yet loaded -> time for scanning scope from 18 seconds to 8 seconds on IntelliJ project
          return VfsUtilCore.loadText(vFile, 5).startsWith("#!");
        }
        catch (IOException e) {
          return false;
        }
      }
    }
    PsiElement firstChild = containingFile.getFirstChild();
    if (firstChild instanceof PsiImportList && firstChild.getTextLength() == 0) {
      PsiElement sibling = firstChild.getNextSibling();
      if (sibling instanceof PsiClass) {
        firstChild = sibling.getFirstChild();
      }
    }
    return firstChild instanceof PsiComment &&
           ((PsiComment)firstChild).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT &&
           firstChild.getText().startsWith("#!");
  }
}
