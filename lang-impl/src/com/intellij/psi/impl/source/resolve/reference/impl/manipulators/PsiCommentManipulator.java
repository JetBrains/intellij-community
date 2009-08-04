package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class PsiCommentManipulator extends AbstractElementManipulator<PsiComment> {
  public PsiComment handleContentChange(PsiComment psiComment, TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = psiComment.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final FileType type = psiComment.getContainingFile().getFileType();
    final PsiFile fromText =
      PsiFileFactory.getInstance(psiComment.getProject()).createFileFromText("__." + type.getDefaultExtension(), newText);

    final PsiComment newElement = PsiTreeUtil.getParentOfType(fromText.findElementAt(0), psiComment.getClass(), false);
    assert newElement != null;
    return (PsiComment)psiComment.replace(newElement);
  }

  @Override
  public TextRange getRangeInElement(final PsiComment element) {
    final String text = element.getText();
    if (text.startsWith("//")) return new TextRange(2, element.getTextLength());
    if (text.startsWith("/**") && text.endsWith("*/")) return new TextRange(3, element.getTextLength()-2);
    if (text.startsWith("/*") && text.endsWith("*/")) return new TextRange(2, element.getTextLength()-2);
    return super.getRangeInElement(element);
  }
}