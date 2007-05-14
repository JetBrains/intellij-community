package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
public class EditInspectionToolsSettingsInSuppressedPlaceIntention implements IntentionAction {
  private String myId;
  private String myDisplayName;

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.family");
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("edit.inspection.options", myDisplayName);
  }

  private static String getSuppressedId(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof PsiFile)) {
      String suppressedIds = InspectionManagerEx.getSuppressedInspectionIdsIn(element);
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
      element = element.getParent();
    }
    return null;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myId = getSuppressedId(editor, file);
    if (myId != null) {
      InspectionProfileEntry tool = getTool(project, file);
      if (tool == null) return false;
      myDisplayName = tool.getDisplayName();
    }
    return myId != null;
  }

  private InspectionProfileEntry getTool(final Project project, final PsiFile file) {
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)projectProfileManager.getInspectionProfile(file);
    return inspectionProfile.getToolById(myId);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InspectionProfileEntry tool = getTool(project, file);
    if (tool == null) return;
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)projectProfileManager.getInspectionProfile(file);
    EditInspectionToolsSettingsAction.editToolSettings(project, inspectionProfile, false, tool.getShortName());
  }

  public boolean startInWriteAction() {
    return false;
  }
}
