package com.intellij.psi.impl.file;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Mossienko
 *         Date: Sep 18, 2008
 *         Time: 3:13:17 PM
 */
public abstract class UpdateAddedFileProcessor {
  private static final ExtensionPointName<UpdateAddedFileProcessor> EP_NAME = ExtensionPointName.create("com.intellij.updateAddedFileProcessor");

  public abstract boolean canProcessElement(PsiFile element);

  public abstract void update(PsiFile element, @Nullable PsiFile originalElement) throws IncorrectOperationException;

  @Nullable
  public static UpdateAddedFileProcessor forElement(PsiFile element) {
    for(UpdateAddedFileProcessor processor: Extensions.getExtensions(EP_NAME)) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return null;
  }
}
