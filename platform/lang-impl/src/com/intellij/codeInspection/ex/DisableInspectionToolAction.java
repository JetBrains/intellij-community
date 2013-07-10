/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Iconable;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

public class DisableInspectionToolAction implements IntentionAction, Iconable {
  private final String myToolId;
  public static final String NAME = InspectionsBundle.message("disable.inspection.action.name");

  public DisableInspectionToolAction(LocalInspectionTool tool) {
    myToolId = tool.getShortName();
  }

  public DisableInspectionToolAction(final HighlightDisplayKey key) {
    myToolId = key.toString();
  }

  @Override
  @NotNull
  public String getText() {
    return NAME;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return NAME;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    InspectionToolWrapper toolWrapper = inspectionProfile.getInspectionTool(myToolId, project);
    return toolWrapper == null || toolWrapper.getDefaultLevel() != HighlightDisplayLevel.NON_SWITCHABLE_ERROR;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(file.getProject());
    InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    ModifiableModel model = inspectionProfile.getModifiableModel();
    model.disableTool(myToolId, file);
    try {
      model.commit();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
