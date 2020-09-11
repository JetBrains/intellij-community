// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class QuickPreviewAction extends ShowImplementationsAction {
  @Override
  protected void triggerFeatureUsed(@NotNull Project project) {
    triggerFeatureUsed(project, "codeassist.quickpreview", "codeassist.quickpreview.lookup");
  }

  @Override
  protected boolean couldPinPopup() {
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (isQuickPreviewAvailableFor(e)) {
      super.update(e);
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected boolean isQuickPreviewAvailableFor(AnActionEvent e) {
    Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JTree || component instanceof JList) {
      SpeedSearchSupply supply = SpeedSearchSupply.getSupply((JComponent)component);
      if (supply == null) {
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        return virtualFile != null || psiElement != null;
      }
    }
    return false;
  }
}
