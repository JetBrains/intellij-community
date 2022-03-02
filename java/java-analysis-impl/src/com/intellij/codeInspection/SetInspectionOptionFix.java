// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionOptionContainer;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class SetInspectionOptionFix implements OnTheFlyLocalFix, LowPriorityAction, Iconable {
  private final String myShortName;
  private final String myProperty;
  private final @IntentionName String myMessage;
  private final boolean myValue;
  @Nullable
  private final Function<InspectionProfileEntry, InspectionProfileEntry> myExtractor;

  public SetInspectionOptionFix(LocalInspectionTool inspection, @NonNls String property, @IntentionName String message, boolean value) {
    this(inspection.getShortName(), property, message, value, null);
  }

  private SetInspectionOptionFix(@NotNull String shortName, @NonNls String property, @IntentionName String message, boolean value,
                                 @Nullable Function<InspectionProfileEntry, InspectionProfileEntry> extractor) {
    myShortName = shortName;
    myProperty = property;
    myMessage = message;
    myValue = value;
    myExtractor = extractor;
  }

  /**
   * @param extractor may be useful for composed inspections e.g. unused declaration, when you need to unwrap a nested inspection's instance
   */
  @NotNull
  public static SetInspectionOptionFix createFix(@NotNull String shortName, @NonNls String property, @IntentionName String message, boolean value,
                                                 @NotNull Function<InspectionProfileEntry, InspectionProfileEntry> extractor) {
    return new SetInspectionOptionFix(shortName, property, message, value, extractor);
  }

  @NotNull
  @Override
  public String getName() {
    return myMessage;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("set.inspection.option.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement().getContainingFile());
  }

  public void applyFix(@NotNull Project project, @NotNull PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    setOption(project, vFile, myValue);
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        setOption(project, vFile, !myValue);
      }

      @Override
      public void redo() {
        setOption(project, vFile, myValue);
      }
    });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull ProblemDescriptor previewDescriptor) {
    InspectionToolWrapper<?, ?> tool =
      InspectionProfileManager.getInstance(project).getCurrentProfile().getInspectionTool(myShortName, previewDescriptor.getPsiElement());
    if (tool == null) return IntentionPreviewInfo.EMPTY;
    JComponent panel = tool.getTool().createOptionsPanel();
    if (!(panel instanceof InspectionOptionContainer)) return IntentionPreviewInfo.EMPTY;
    HtmlChunk label = ((InspectionOptionContainer)panel).getLabelForCheckbox(myProperty);
    HtmlChunk.Element checkbox = HtmlChunk.tag("input").attr("type", "checkbox").attr("readonly", "true");
    if (myValue) {
      checkbox = checkbox.attr("checked", "true");
    }
    HtmlChunk info = HtmlChunk.tag("table")
      .child(HtmlChunk.tag("tr").children(
        HtmlChunk.tag("td").child(checkbox),
        HtmlChunk.tag("td").child(label)
      ));
    return new IntentionPreviewInfo.Html(
      new HtmlBuilder().append(myValue ? QuickFixBundle.message("set.inspection.option.description.check")
                                       : QuickFixBundle.message("set.inspection.option.description.uncheck"))
        .br().br().append(info).toFragment());
  }

  private void setOption(@NotNull Project project, @NotNull VirtualFile vFile, boolean value) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) return;
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
      InspectionToolWrapper<?, ?> tool = model.getInspectionTool(myShortName, file);
      if (tool == null) return;
      InspectionProfileEntry inspection = tool.getTool();
      if (myExtractor != null) {
        inspection = myExtractor.apply(inspection);
      }
      ReflectionUtil.setField(inspection.getClass(), inspection, boolean.class, myProperty, value);
    });
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
