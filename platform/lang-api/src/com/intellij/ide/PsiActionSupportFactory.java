// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PsiActionSupportFactory {
  public static PsiActionSupportFactory getInstance() {
    return ApplicationManager.getApplication().getService(PsiActionSupportFactory.class);
  }

  public interface PsiElementSelector {
    PsiElement @NotNull [] getSelectedElements();
  }

  public abstract CopyPasteSupport createPsiBasedCopyPasteSupport(Project project, JComponent keyReceiver, PsiElementSelector dataSelector);

  public abstract DeleteProvider createPsiBasedDeleteProvider();
}
