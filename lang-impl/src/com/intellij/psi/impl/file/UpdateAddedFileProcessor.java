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

  protected boolean isDefault() {
    return false;
  }

  public abstract void update(PsiFile element) throws IncorrectOperationException;

  @Nullable
  public static UpdateAddedFileProcessor forElement(PsiFile element) {
    UpdateAddedFileProcessor defaultProcessor = null;
    
    for(UpdateAddedFileProcessor processor: Extensions.getExtensions(EP_NAME)) {
      if (processor.isDefault()) {
        defaultProcessor = processor;
        continue;
      }

      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return defaultProcessor;
  }
}
