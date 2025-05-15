// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.options.LocMessage;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptNumber;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.text.HtmlChunk.*;

/**
 * @deprecated use {@link UpdateInspectionOptionFix} instead.
 */
@Deprecated(forRemoval = true)
public class SetInspectionOptionFix extends IntentionAndQuickFixAction implements LowPriorityAction, Iconable {
  private final String myShortName;
  private final String myProperty;
  private final @IntentionName String myMessage;
  private final Object myValue;

  public SetInspectionOptionFix(LocalInspectionTool inspection, @NonNls String property, @IntentionName String message, boolean value) {
    this(inspection.getShortName(), property, message, value);
  }

  public SetInspectionOptionFix(LocalInspectionTool inspection, @NonNls String property, @IntentionName String message, int value) {
    this(inspection.getShortName(), property, message, value);
  }

  private SetInspectionOptionFix(@NotNull String shortName, @NonNls String property, @IntentionName String message, Object value) {
    myShortName = shortName;
    myProperty = property;
    myMessage = message;
    myValue = value;
  }

  @Override
  public @NotNull String getName() {
    return myMessage;
  }

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("set.inspection.option.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean availableInBatchMode() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor) {
    VirtualFile vFile = psiFile.getVirtualFile();
    Object oldValue = getOption(project, vFile);
    setOption(project, vFile, myValue);
    if (oldValue != null) {
      UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
        @Override
        public void undo() {
          setOption(project, vFile, oldValue);
        }

        @Override
        public void redo() {
          setOption(project, vFile, myValue);
        }
      });
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return generatePreviewForFile(previewDescriptor.getPsiElement().getContainingFile());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return generatePreviewForFile(psiFile);
  }

  private @NotNull IntentionPreviewInfo generatePreviewForFile(@NotNull PsiFile psiFile) {
    Project project = psiFile.getProject();
    InspectionToolWrapper<?, ?> tool =
      InspectionProfileManager.getInstance(project).getCurrentProfile().getInspectionTool(myShortName, psiFile);
    if (tool == null) return IntentionPreviewInfo.EMPTY;
    InspectionProfileEntry inspection = getLocalTool(tool);
    OptPane pane = inspection.getOptionsPane();
    if (myValue instanceof Boolean value) {
      OptCheckbox control = ObjectUtils.tryCast(pane.findControl(myProperty), OptCheckbox.class);
      if (control == null) return IntentionPreviewInfo.EMPTY;
      HtmlChunk label = text(control.label().label());
      Element checkbox = tag("input").attr("type", "checkbox").attr("readonly", "true");
      if (value) {
        checkbox = checkbox.attr("checked", "true");
      }
      HtmlChunk info = tag("table")
        .child(tag("tr").children(
          tag("td").child(checkbox),
          tag("td").child(label)
        ));
      return new IntentionPreviewInfo.Html(
        new HtmlBuilder().append(value ? AnalysisBundle.message("set.option.description.check")
                                       : AnalysisBundle.message("set.option.description.uncheck"))
          .br().br().append(info).toFragment());
    } else if (myValue instanceof Integer value) {
      OptNumber control = ObjectUtils.tryCast(pane.findControl(myProperty), OptNumber.class);
      if (control == null) return IntentionPreviewInfo.EMPTY;
      LocMessage.PrefixSuffix prefixSuffix = control.splitLabel().splitLabel();
      Element input = tag("input").attr("type", "text").attr("value", value)
        .attr("size", value.toString().length() + 1).attr("readonly", "true");
      HtmlChunk info = tag("table").child(tag("tr").children(
        tag("td").child(text(prefixSuffix.prefix())),
        tag("td").child(input),
        tag("td").child(text(prefixSuffix.suffix()))
      ));
      return new IntentionPreviewInfo.Html(
        new HtmlBuilder().append(AnalysisBundle.message("set.option.description.input"))
          .br().br().append(info).br().toFragment());
    }
    else {
      throw new IllegalStateException("Value of type " + myValue.getClass() + " is not supported");
    }
  }

  private static InspectionProfileEntry getLocalTool(InspectionToolWrapper<?, ?> tool) {
    InspectionProfileEntry inspection = tool.getTool();
    if (inspection instanceof GlobalInspectionTool global) {
      return global.getSharedLocalInspectionTool();
    }
    return inspection;
  }

  private Object getOption(@NotNull Project project, @NotNull VirtualFile vFile) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) return null;
    InspectionToolWrapper<?, ?> tool = InspectionProfileManager.getInstance(project).getCurrentProfile().getInspectionTool(myShortName, file);
    if (tool == null) return null;
    InspectionProfileEntry inspection = getLocalTool(tool);
    return inspection.getOptionController().getOption(myProperty);
  }

  private void setOption(@NotNull Project project, @NotNull VirtualFile vFile, Object value) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) return;
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
      InspectionToolWrapper<?, ?> tool = model.getInspectionTool(myShortName, file);
      if (tool == null) return;
      InspectionProfileEntry inspection = getLocalTool(tool);
      inspection.getOptionController().setOption(myProperty, value);
    });
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
