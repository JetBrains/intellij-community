// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiCommentManipulator extends AbstractElementManipulator<PsiComment> {
  @Override
  public PsiComment handleContentChange(@NotNull PsiComment psiComment, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = psiComment.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    FileType type = psiComment.getContainingFile().getFileType();
    PsiFile fromText = PsiFileFactory.getInstance(psiComment.getProject()).createFileFromText("__." + type.getDefaultExtension(), type, newText);
    PsiComment newElement = PsiTreeUtil.getParentOfType(fromText.findElementAt(0), psiComment.getClass(), false);
    assert newElement != null : type + " " + type.getDefaultExtension() + " " + newText;
    return (PsiComment)psiComment.replace(newElement);
  }

  @Override
  public @NotNull TextRange getRangeInElement(final @NotNull PsiComment element) {
    final String text = element.getText();
    if (text.startsWith("//")) return new TextRange(2, element.getTextLength());
    final int length = text.length();
    if (length > 4 && text.startsWith("/**") && text.endsWith("*/")) return new TextRange(3, element.getTextLength()-2);
    if (length > 3 && (text.startsWith("/*") && text.endsWith("*/") ||
                       text.startsWith("(*") && text.endsWith("*)"))) return new TextRange(2, element.getTextLength()-2);
    if (length > 6 && text.startsWith("<!--") && text.endsWith("-->")) return new TextRange(4, element.getTextLength()-3);
    if (text.startsWith("--")) return new TextRange(2, element.getTextLength());
    if (text.startsWith("#")) return new TextRange(1, element.getTextLength());
    return super.getRangeInElement(element);
  }
}