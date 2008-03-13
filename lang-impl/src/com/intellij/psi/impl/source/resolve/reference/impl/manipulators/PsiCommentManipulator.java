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
 * @author maxim.mossienko
 */
public class PsiCommentManipulator extends AbstractElementManipulator<PsiComment> {
  public PsiComment handleContentChange(PsiComment xmlToken, TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = xmlToken.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final FileType type = xmlToken.getContainingFile().getFileType();
    final PsiFile fromText =
      PsiFileFactory.getInstance(xmlToken.getProject()).createFileFromText("__." + type.getDefaultExtension(), newText);

    final PsiComment newElement = PsiTreeUtil.getParentOfType(fromText.findElementAt(0), xmlToken.getClass(), false);
    assert newElement != null;
    return (PsiComment)xmlToken.replace(newElement);
  }
}