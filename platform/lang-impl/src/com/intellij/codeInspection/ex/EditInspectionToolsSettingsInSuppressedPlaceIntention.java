// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditInspectionToolsSettingsInSuppressedPlaceIntention implements IntentionAction {
  private String myDisplayName;

  @Override
  public @NotNull String getFamilyName() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.family");
  }

  @Override
  public @NotNull String getText() {
    return InspectionsBundle.message("edit.inspection.options", myDisplayName);
  }

  private static @Nullable String getSuppressedId(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof PsiFile)) {
      for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
        final String suppressedIds = factory.getSuppressedInspectionIdsIn(element);
        if (suppressedIds != null) {
          for (String id : StringUtil.split(suppressedIds, ",")) {
            if (isCaretOnSuppressedId(file, offset, id)) {
              return id;
            }
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }

  private static boolean isCaretOnSuppressedId(PsiFile file, int caretOffset, String suppressedId) {
    CharSequence fileText = file.getViewProvider().getContents();
    int start = Math.max(0, caretOffset - suppressedId.length());
    int end = Math.min(caretOffset + suppressedId.length(), fileText.length());
    return StringUtil.indexOf(fileText.subSequence(start, end), suppressedId) >= 0;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    String suppressedId = getSuppressedId(editor, file);
    if (suppressedId != null) {
      InspectionToolWrapper toolWrapper = getTool(project, file, suppressedId);
      if (toolWrapper == null) return false;
      myDisplayName = toolWrapper.getDisplayName();
    }
    return suppressedId != null;
  }

  private static @Nullable InspectionToolWrapper getTool(final Project project, final PsiFile file, final String suppressId) {
    if (suppressId == null) return null;
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = projectProfileManager.getCurrentProfile();
    return inspectionProfile.getToolById(suppressId, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InspectionToolWrapper toolWrapper = getTool(project, file, getSuppressedId(editor, file));
    if (toolWrapper == null) return;
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = projectProfileManager.getCurrentProfile();
    EditInspectionToolsSettingsAction.editToolSettings(project, inspectionProfile, toolWrapper.getShortName());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(InspectionsBundle.message("edit.inspection.options.preview", myDisplayName));
  }
}
