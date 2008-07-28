package com.intellij.codeInsight.template;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.codeInsight.template.impl.TemplateContext;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 28, 2008
 * Time: 4:53:26 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractContextType implements TemplateContextType {
  public boolean isInContext(final PsiFile file, final int offset) {
    return isInContext(file.getFileType());
  }

  public boolean isInContext(final FileType fileType) {
    if (fileType == null) {
      return false;
    }
    else {
      return fileType == getExpectedFileType();
    }
  }

  protected abstract LanguageFileType getExpectedFileType();

  public boolean isEnabled(final TemplateContext context) {
    return getContextElement(context).getValue();
  }

  protected abstract TemplateContext.ContextElement getContextElement(TemplateContext context);

  public void setEnabled(final TemplateContext context, final boolean value) {
    getContextElement(context).setValue(value);
  }

  public SyntaxHighlighter createHighlighter() {
    LanguageFileType fileType = getExpectedFileType();
    return  fileType == null ? null : SyntaxHighlighter.PROVIDER.create(fileType, null, null);
  }
}
