package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 *         Date: Sep 18, 2008
 *         Time: 3:40:48 PM
 */
public abstract class MoveFileHandler {
  private static final ExtensionPointName<MoveFileHandler> EP_NAME = ExtensionPointName.create("com.intellij.moveFileHandler");

  public abstract boolean canProcessElement(PsiFile element);
  public abstract void prepareMovedFile(PsiFile file);

  public abstract void updateMovedFile(PsiFile file) throws IncorrectOperationException;

  @NotNull
  public static MoveFileHandler forElement(PsiFile element) {
    for(MoveFileHandler processor: Extensions.getExtensions(EP_NAME)) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return DEFAULT;
  }

  private static MoveFileHandler DEFAULT = new MoveFileHandler() {
    @Override
    public boolean canProcessElement(final PsiFile element) {
      return true;
    }

    @Override
    public void prepareMovedFile(final PsiFile file) {

    }

    @Override
    public void updateMovedFile(final PsiFile file) throws IncorrectOperationException {

    }
  };
}
