// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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
    super(method, method, new ExtractableFragment(PsiElement.EMPTY_ARRAY));
    myIcon = method.getIcon(0);
    setAllowsChildren(false);
  }

  @NotNull
  @Override
  protected TextChunk[] createTextChunks(@NotNull PsiElement element) {
    assert element instanceof PsiMethod;
    Project project = element.getProject();

    // EditorHighlighter requires VirtualFile, let's make sure it exists. Also, file.getText() is much cheaper with this.
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText(
      "A.java", JavaLanguage.INSTANCE, "class A{" + element.getText() + "}");
    PsiMethod psiMethod = file.getClasses()[0].getMethods()[0];

    UsageInfo2UsageAdapter usageAdapter = new UsageInfo2UsageAdapter(new UsageInfo(psiMethod));
    TextRange range = psiMethod.getTextRange();
    PsiIdentifier identifier = psiMethod.getNameIdentifier();
    int startOffset = identifier != null ? identifier.getTextRange().getStartOffset() : range.getStartOffset();
    int endOffset = Math.min(psiMethod.getParameterList().getTextRange().getEndOffset(), range.getEndOffset());
    return ChunkExtractor.getExtractor(file)
                         .createTextChunks(usageAdapter, file.getText(), startOffset, endOffset, false, new ArrayList<>());
  }

  public Icon getIcon() {
    return myIcon;
  }
}
