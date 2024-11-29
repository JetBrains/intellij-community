// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SuppressIntentionActionFromFix extends SuppressIntentionAction implements Comparable<IntentionAction> {
  private final SuppressQuickFix myFix;

  private SuppressIntentionActionFromFix(@NotNull SuppressQuickFix fix) {
    myFix = fix;
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myFix.getElementToMakeWritable(currentFile);
  }

  @Override
  public int compareTo(@NotNull IntentionAction o) {
    if (o instanceof SuppressIntentionActionFromFix otherSuppressFix) {
      final int i = getFixPriority() - otherSuppressFix.getFixPriority();
      if (i != 0) return i;
    }

    return Comparing.compare(getFamilyName(), o.getFamilyName());
  }

  public static @NotNull SuppressIntentionAction convertBatchToSuppressIntentionAction(final @NotNull SuppressQuickFix fix) {
    return new SuppressIntentionActionFromFix(fix);
  }

  public static SuppressIntentionAction @NotNull [] convertBatchToSuppressIntentionActions(SuppressQuickFix @NotNull [] actions) {
    return ContainerUtil.map2Array(actions, SuppressIntentionAction.class,
                                   SuppressIntentionActionFromFix::convertBatchToSuppressIntentionAction);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement container = getContainer(element);
    boolean caretWasBeforeStatement = editor != null && container != null && editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
    InspectionManager inspectionManager = InspectionManager.getInstance(project);
    ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(element, element, "", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
    myFix.applyFix(project, descriptor);

    if (caretWasBeforeStatement) {
      editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
    }
  }

  public ThreeState isShouldBeAppliedToInjectionHost() {
    return myFix instanceof InjectionAwareSuppressQuickFix
           ? ((InjectionAwareSuppressQuickFix)myFix).isShouldBeAppliedToInjectionHost()
           : ThreeState.UNSURE;
  }

  public PsiElement getContainer(PsiElement element) {
    return myFix instanceof ContainerBasedSuppressQuickFix ? ((ContainerBasedSuppressQuickFix)myFix).getContainer(element) : null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return myFix.isAvailable(project, element);
  }

  @Override
  public @NotNull @IntentionName String getText() {
    return isShouldBeAppliedToInjectionHost() == ThreeState.NO
           ? AnalysisBundle.message("intention.name.in.injection", myFix.getName())
           : myFix.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFix.getFamilyName();
  }

  @Override
  public boolean isSuppressAll() {
    return myFix.isSuppressAll();
  }

  @ApiStatus.Internal
  public int getFixPriority() {
    return myFix.getPriority();
  }

  @Override
  public @Nullable ModCommandAction asModCommandAction() {
    if (!(myFix instanceof ModCommandQuickFix mcFix)) return null;
    return new ModCommandAction() {
      @Override
      public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
        if (InjectedLanguageManager.getInstance(context.project()).isInjectedFragment(context.file()) &&
            isShouldBeAppliedToInjectionHost() == ThreeState.YES) {
          return null;
        }
        PsiElement element = context.findLeaf();
        if (element == null || !myFix.isAvailable(context.project(), element)) return null;
        return Presentation.of(mcFix.getName());
      }

      @Override
      public @NotNull ModCommand perform(@NotNull ActionContext context) {
        PsiElement element = Objects.requireNonNull(context.findLeaf());
        InspectionManager inspectionManager = InspectionManager.getInstance(context.project());
        PsiElement container = getContainer(element);
        boolean caretWasBeforeStatement = container != null && context.offset() == container.getTextRange().getStartOffset();
        ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(
          element, element, "", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
        ModCommand command = mcFix.perform(context.project(), descriptor);
        if (caretWasBeforeStatement) {
          command = ModCommand.moveCaretAfter(command, container.getContainingFile(), context.offset(), true);
        }
        return command;
      }

      @Override
      public @NotNull String getFamilyName() {
        return mcFix.getFamilyName();
      }
    };
  }
}
