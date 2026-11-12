// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.NewClassPlace;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CreateClassFromUsageFix extends CreateClassFromUsageBaseFix {
  public CreateClassFromUsageFix(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    ActionContext context = ActionContext.from(editor, psiFile.getOriginalFile());
    ModCommandAction action = getFallbackModCommandAction();
    return action == null ? IntentionPreviewInfo.EMPTY : action.generatePreview(context);
  }

  @Override
  public String getText(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", myKind.getDescriptionAccusative(), varName);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return;

    NewClassPlace place = NewClassPlace.choosePlaceInteractively(element, myKind);
    if (place == null) return;
    ActionContext context = ActionContext.from(editor, psiFile);
    ModCommandExecutor.executeInteractively(context, getText(), editor, () -> createCommand(context, element, place));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private @NotNull ModCommand createCommand(@NotNull ActionContext context,
                                            @NotNull PsiJavaCodeReferenceElement element,
                                            @NotNull NewClassPlace place) {
    return ModCommand.psiUpdate(context, updater -> {
      PsiJavaCodeReferenceElement writableElement = updater.getWritable(element);
      PsiClass aClass = CreateFromUsageUtils.createClass(updater, myKind, place, element, getSuperClassName(element));
      if (aClass == null) return;
      RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
      PsiDeconstructionPattern pattern = getDeconstructionPattern(element);
      if (pattern != null) {
        CreateRecordFromNewFix.setupRecordComponentsFromPattern(aClass.getRecordHeader(), fields, pattern.getDeconstructionList());
      }
      writableElement.bindToElement(aClass);
      fields.startTemplate(updater, aClass, null);
    });
  }

  @Override
  public @Nullable ModCommandAction getFallbackModCommandAction() {
    PsiJavaCodeReferenceElement element = getRefElement();
    return element == null ? null : new CreateClassFromUsageModCommandAction(element);
  }

  /**
   * Creates the class without a dialog. A new file goes into the directory of the current file. The fix which the user starts
   * in the editor asks for the directory, which a {@link ModCommandAction} cannot do.
   */
  private final class CreateClassFromUsageModCommandAction extends PsiBasedModCommandAction<PsiJavaCodeReferenceElement> {
    private CreateClassFromUsageModCommandAction(@NotNull PsiJavaCodeReferenceElement element) {
      super(element);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateClassFromUsageFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
      if (NewClassPlace.defaultPlaceFor(element) == null) return null;
      String text = getAvailableText(element, context.offset());
      return text == null ? null : Presentation.of(text);
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
      NewClassPlace place = NewClassPlace.defaultPlaceFor(element);
      if (place == null) return ModCommand.nop();
      return createCommand(context, element, place);
    }
  }
}
