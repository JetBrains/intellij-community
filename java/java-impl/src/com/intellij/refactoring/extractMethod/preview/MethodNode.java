// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Pavel.Dolgov
 */
class MethodNode extends FragmentNode {
  private final Icon myIcon;

  public MethodNode(@NotNull PsiMethod method) {
    super(method, method);
    myIcon = method.getIcon(0);
    setAllowsChildren(false);
  }

  @Override
  protected TextChunk[] createTextChunks(@NotNull PsiElement start, @NotNull PsiElement end) {
    assert start == end && start instanceof PsiMethod;
    Project project = start.getProject();
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText(
      "A.java", JavaLanguage.INSTANCE, "class A{" + start.getText() + "}");
    PsiMethod psiMethod = file.getClasses()[0].getMethods()[0];

    UsageInfo2UsageAdapter usageAdapter = new UsageInfo2UsageAdapter(new UsageInfo(psiMethod));
    String text = psiMethod.getText();
    int endOffset = Math.min(text.length(),
                             psiMethod.getParameterList().getStartOffsetInParent() +
                             psiMethod.getParameterList().getTextRange().getLength());
    return ChunkExtractor.getExtractor(psiMethod.getContainingFile())
                         .createTextChunks(usageAdapter, text, 0, endOffset, true, new ArrayList<>());
  }

  public Icon getIcon() {
    return myIcon;
  }
}
