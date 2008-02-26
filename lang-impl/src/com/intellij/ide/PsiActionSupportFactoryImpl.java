package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ide.util.DeleteHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PsiActionSupportFactoryImpl extends PsiActionSupportFactory {
  public CopyPasteSupport createPsiBasedCopyPasteSupport(final Project project, final JComponent keyReceiver,
                                                         final PsiElementSelector dataSelector) {
    return new CopyPasteDelegator(project, keyReceiver) {
      @NotNull
      protected PsiElement[] getSelectedElements() {
        PsiElement[] elements = dataSelector.getSelectedElements();
        return elements == null ? PsiElement.EMPTY_ARRAY : elements;
      }
    };
  }

  public DeleteProvider createPsiBasedDeleteProvider() {
    return new DeleteHandler.DefaultDeleteProvider();
  }
}
