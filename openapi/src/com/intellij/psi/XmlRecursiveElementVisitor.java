/*
 * @author max
 */
package com.intellij.psi;

import java.util.List;

public class XmlRecursiveElementVisitor extends XmlElementVisitor {
  private final boolean myVisitAllFileRoots;

  public XmlRecursiveElementVisitor() {
    myVisitAllFileRoots = false;
  }

  public XmlRecursiveElementVisitor(final boolean visitAllFileRoots) {
    myVisitAllFileRoots = visitAllFileRoots;
  }

  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }

  @Override
  public void visitFile(final PsiFile file) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (file == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
          }
          return;
        }
      }
    }

    super.visitFile(file);
  }
}