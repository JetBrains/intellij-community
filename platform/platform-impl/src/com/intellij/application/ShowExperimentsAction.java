// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class ShowExperimentsAction extends DumbAwareAction implements LightEditCompatible {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new ExperimentsDialog(e.getProject()).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean hasExperimentalFeatures = Experiments.EP_NAME.getExtensions().length > 0;
    e.getPresentation().setEnabledAndVisible(hasExperimentalFeatures);
  }
}
