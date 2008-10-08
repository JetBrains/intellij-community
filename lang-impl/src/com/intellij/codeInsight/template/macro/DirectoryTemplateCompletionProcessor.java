package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFileSystemItem;

/**
 * @author yole
 */
public class DirectoryTemplateCompletionProcessor implements TemplateCompletionProcessor {
  public boolean nextTabOnItemSelected(final ExpressionContext context, final LookupElement item) {
    if (item.getObject() instanceof PsiFileSystemItem) {
      final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)item.getObject();
      if (fileSystemItem.isDirectory()) {
        return false;
      }
    }
    return true;
  }
}
