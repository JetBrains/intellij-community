// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeEditor;

import com.intellij.openapi.fileEditor.impl.EditorFileSwapper;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.Pair.pair;

public class JavaEditorFileSwapper extends EditorFileSwapper {
  @Override
  public Pair<VirtualFile, Integer> getFileToSwapTo(Project project, EditorWithProviderComposite editor) {
    VirtualFile file = editor.getFile();
    VirtualFile sourceFile = findSourceFile(project, file);
    if (sourceFile == null) return null;

    Integer position = null;

    TextEditorImpl oldEditor = findSinglePsiAwareEditor(editor.getEditors());
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

    return pair(sourceFile, position);
  }

  @Nullable
  public static VirtualFile findSourceFile(@NotNull Project project, @NotNull VirtualFile file) {
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