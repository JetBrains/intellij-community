package com.intellij.codeInsight.template;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaCommentContextType extends TemplateContextType {
  public JavaCommentContextType() {
    super("JAVA_COMMENT", CodeInsightBundle.message("dialog.edit.template.checkbox.java.comment"));
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace && offset > 0) {
        element = file.findElementAt(offset-1);
      }
      return PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
    }
    return false;
  }

  @Override
  public boolean isInContext(@NotNull FileType fileType) {
    return false;
  }

}
