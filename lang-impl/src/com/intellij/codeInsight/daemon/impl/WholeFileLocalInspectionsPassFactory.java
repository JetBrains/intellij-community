package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
public class WholeFileLocalInspectionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public WholeFileLocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    // can run in the same time with LIP, but should start after it, since I believe whole-file inspections would run longer
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.LOCAL_INSPECTIONS}, true, -1);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "WholeFileLocalInspectionsPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.LOCAL_INSPECTIONS);
    if (textRange == null || !wholeFileToolsDefined(file)) return null;
    return new LocalInspectionsPass(file, editor.getDocument(), 0, file.getTextLength()) {
      List<LocalInspectionTool> getInspectionTools(InspectionProfileWrapper profile) {
        List<LocalInspectionTool> tools = super.getInspectionTools(profile);
        List<LocalInspectionTool> result = new ArrayList<LocalInspectionTool>();
        for (LocalInspectionTool tool : tools) {
          if (tool.runForWholeFile()) result.add(tool);
        }
        return result;
      }

      void inspectInjectedPsi(PsiElement[] elements, List<LocalInspectionTool> tools) {
        // inspected in LIP already
      }
    };
  }

  private boolean wholeFileToolsDefined(PsiFile file) {
    final InspectionProfileWrapper profile = InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper();
    final List<LocalInspectionTool> tools = profile.getHighlightingLocalInspectionTools(file);
    for (LocalInspectionTool tool : tools) {
      if (tool.runForWholeFile()) return true;
    }
    return false;
  }
}