/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class EditInspectionToolsSettingsInSuppressedPlaceIntention implements IntentionAction {
  private String myId;
  private String myDisplayName;

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.family");
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("edit.inspection.options", myDisplayName);
  }

  @Nullable
  private static String getSuppressedId(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof PsiFile)) {
      for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
        final String suppressedIds = factory.getSuppressedInspectionIdsIn(element);
        if (suppressedIds != null) {
          String text = element.getText();
          List<String> ids = StringUtil.split(suppressedIds, ",");
          for (String id : ids) {
            int i = text.indexOf(id);
            if (i == -1) continue;
            int idOffset = element.getTextRange().getStartOffset() + i;
            if (TextRange.from(idOffset, id.length()).contains(offset)) {
              return id;
            }
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myId = getSuppressedId(editor, file);
    if (myId != null) {
      InspectionToolWrapper toolWrapper = getTool(project, file);
      if (toolWrapper == null) return false;
      myDisplayName = toolWrapper.getDisplayName();
    }
    return myId != null;
  }

  @Nullable
  private InspectionToolWrapper getTool(final Project project, final PsiFile file) {
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)projectProfileManager.getInspectionProfile();
    return inspectionProfile.getToolById(myId, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InspectionToolWrapper toolWrapper = getTool(project, file);
    if (toolWrapper == null) return;
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)projectProfileManager.getInspectionProfile();
    EditInspectionToolsSettingsAction.editToolSettings(project, inspectionProfile, false, toolWrapper.getShortName());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
