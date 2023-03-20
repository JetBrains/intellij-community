// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor;

import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorFileSwapper;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.util.PsiTreeUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaEditorFileSwapper implements EditorFileSwapper {
  @Override
  public Pair<VirtualFile, @Nullable Integer> getFileToSwapTo(Project project, EditorComposite composite) {
    VirtualFile file = composite.getFile();
    VirtualFile sourceFile = findSourceFile(project, file);
    if (sourceFile == null) {
      return null;
    }

    Integer position = null;

    TextEditorImpl oldEditor = EditorFileSwapper.findSinglePsiAwareEditor(composite.getAllEditors());
    if (oldEditor != null) {
      PsiCompiledFile clsFile = (PsiCompiledFile)PsiManager.getInstance(project).findFile(file);
      assert clsFile != null;

      int offset = oldEditor.getEditor().getCaretModel().getOffset();
      PsiElement elementAt = clsFile.findElementAt(offset);
      PsiMember member = PsiTreeUtil.getParentOfType(elementAt, PsiMember.class, false);
      if (member != null) {
        PsiElement navigationElement = member.getOriginalElement().getNavigationElement();
        if (Comparing.equal(navigationElement.getContainingFile().getVirtualFile(), sourceFile)) {
          position = navigationElement.getTextOffset();
        }
      }
    }

    return new Pair<>(sourceFile, position);
  }

  public static @Nullable VirtualFile findSourceFile(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PsiCompiledFile && psiFile instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
      if (classes.length != 0 && classes[0] instanceof ClsClassImpl) {
        PsiClass sourceClass = ((ClsClassImpl)classes[0]).getSourceMirrorClass();
        if (sourceClass != null) {
          VirtualFile result = sourceClass.getContainingFile().getVirtualFile();
          assert result != null : sourceClass;
          return result;
        }
      }
    }

    return null;
  }
}