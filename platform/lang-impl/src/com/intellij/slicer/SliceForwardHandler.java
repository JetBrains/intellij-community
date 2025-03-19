// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

final class SliceForwardHandler extends SliceHandler {
  SliceForwardHandler() {
    super(false);
  }

  @Override
  public SliceAnalysisParams askForParams(@NotNull PsiElement element,
                                          SliceManager.@NotNull StoredSettingsBean storedSettingsBean,
                                          @NotNull String dialogTitle) {
    AnalysisScope analysisScope = new AnalysisScope(element.getContainingFile());
    Module module = ModuleUtilCore.findModuleForPsiElement(element);

    Project myProject = element.getProject();
    final SliceForwardAdditionalUi ui = new SliceForwardAdditionalUi();
    ui.getMyShowDerefs().setSelected(storedSettingsBean.showDereferences);

    AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
    analysisUIOptions.loadState(storedSettingsBean.analysisUIOptions);

    List<ModelScopeItem> items = BaseAnalysisActionDialog.standardItems(myProject, analysisScope, module, element);
    BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(dialogTitle, LangBundle.message("separator.analyze.scope"), myProject,
                                                                   items, analysisUIOptions, true) {
      @Override
      protected JComponent getAdditionalActionSettings(@NotNull Project project) {
        return ui.getPanel();
      }
    };
    if (!dialog.showAndGet()) {
      return null;
    }

    storedSettingsBean.analysisUIOptions.loadState(analysisUIOptions);
    storedSettingsBean.showDereferences = ui.getMyShowDerefs().isSelected();

    AnalysisScope scope = dialog.getScope(analysisScope);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = scope;
    params.dataFlowToThis = myDataFlowToThis;
    params.showInstanceDereferences = ui.getMyShowDerefs().isSelected();
    return params;
  }
}