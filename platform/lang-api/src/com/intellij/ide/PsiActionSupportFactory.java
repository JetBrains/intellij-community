package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PsiActionSupportFactory {
  public static PsiActionSupportFactory getInstance() {
    return ServiceManager.getService(PsiActionSupportFactory.class);
  }

  public static interface PsiElementSelector {
    PsiElement[] getSelectedElements();
  }

  public abstract CopyPasteSupport createPsiBasedCopyPasteSupport(Project project, JComponent keyReceiver, PsiElementSelector dataSelector);

  public abstract DeleteProvider createPsiBasedDeleteProvider();
}
