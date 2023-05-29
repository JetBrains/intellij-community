// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public final class QuickFixWrapper implements IntentionAction, PriorityAction, CustomizableIntentionAction {
  private static final Logger LOG = Logger.getInstance(QuickFixWrapper.class);

  private final ProblemDescriptor myDescriptor;
  private final LocalQuickFix myFix;

  @NotNull
  public static IntentionAction wrap(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    LOG.assertTrue(fixNumber >= 0, fixNumber);
    QuickFix<?>[] fixes = descriptor.getFixes();
    LOG.assertTrue(fixes != null && fixes.length > fixNumber);

    final QuickFix<?> fix = fixes[fixNumber];
    if (fix instanceof ModCommandQuickFix modCommandFix) {
      IntentionAction intention = new ModCommandQuickFixAction(descriptor, modCommandFix).asIntention();
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      intention.isAvailable(file.getProject(), null, file); // cache presentation in wrapper
      return intention;
    }
    return fix instanceof IntentionAction ? (IntentionAction)fix : new QuickFixWrapper(descriptor, (LocalQuickFix)fix);
  }

  /**
   * @param action action previously wrapped with {@link #wrap(ProblemDescriptor, int)}
   * @return a {@link LocalQuickFix} wrapped inside that action; null if the action was not created via {@link QuickFixWrapper}
   */
  public static @Nullable LocalQuickFix unwrap(@NotNull CommonIntentionAction action) {
    if (action instanceof QuickFixWrapper wrapper) {
      return wrapper.myFix;
    }
    ModCommandAction modCommand = action instanceof ModCommandAction mc ? mc:
                                  ModCommandAction.unwrap((IntentionAction)action);                              
    if (modCommand instanceof ModCommandQuickFixAction qfAction) {
      return qfAction.myFix;
    }
    return null;
  }

  /**
   * @param action action to extract the file from
   * @return PsiFile the action relates to
   */
  public static @Nullable PsiFile unwrapFile(@NotNull IntentionAction action) {
    if (action instanceof QuickFixWrapper wrapper) {
      return wrapper.getFile();
    }
    if (ModCommandAction.unwrap(action) instanceof ModCommandQuickFixAction qfAction) {
      PsiElement element = qfAction.myDescriptor.getPsiElement();
      return element == null ? null : element.getContainingFile();
    }
    return null;
  }

  private QuickFixWrapper(@NotNull ProblemDescriptor descriptor, @NotNull LocalQuickFix fix) {
    myDescriptor = descriptor;
    myFix = fix;
  }

  @Override
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myFix.getName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return false;
    PsiFile containingFile = psiElement.getContainingFile();
    return containingFile == file || containingFile == null ||
           containingFile.getViewProvider().getVirtualFile().equals(file.getViewProvider().getVirtualFile());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    //if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    // consider all local quick fixes do it themselves

    final PsiElement element = myDescriptor.getPsiElement();
    final PsiFile fileForUndo = element == null ? null : element.getContainingFile();
    myFix.applyFix(project, myDescriptor);
    DaemonCodeAnalyzer.getInstance(project).restart();
    if (fileForUndo != null && !fileForUndo.equals(file)) {
      UndoUtil.markPsiFileForUndo(fileForUndo);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myFix.getElementToMakeWritable(file);
  }

  /**
   * @return fix wrapped by this {@link QuickFixWrapper}
   * @deprecated use {@link QuickFixWrapper#unwrap(CommonIntentionAction)} instead. Avoid {@code instanceof QuickFixWrapper} checks,
   * as the implementation may be different in the future
   */
  @Deprecated
  @NotNull
  public LocalQuickFix getFix() {
    return myFix;
  }

  @NotNull
  @Override
  public Priority getPriority() {
    return myFix instanceof PriorityAction ? ((PriorityAction)myFix).getPriority() : Priority.NORMAL;
  }

  @TestOnly
  public static @Nullable ProblemHighlightType getHighlightType(@NotNull IntentionAction action) {
    if (action instanceof QuickFixWrapper wrapper) {
      return wrapper.myDescriptor.getHighlightType();
    }
    if (ModCommandAction.unwrap(action) instanceof ModCommandQuickFixAction qfAction) {
      return qfAction.myDescriptor.getHighlightType();
    }
    return null;
  }

  @Nullable
  public PsiFile getFile() {
    PsiElement element = myDescriptor.getPsiElement();
    return element != null ? element.getContainingFile() : null;
  }

  public String toString() {
    return getText();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    PsiFile psiFile = getFile();
    PsiFile originalFile = IntentionPreviewUtils.getOriginalFile(file);
    if (originalFile != psiFile) {
      return myFix.generatePreview(project, myDescriptor);
    }
    ProblemDescriptor descriptorForPreview;
    try {
      descriptorForPreview = myDescriptor.getDescriptorForPreview(file);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot create preview descriptor for quickfix " + myFix.getFamilyName() + " (" + myFix.getClass() + ")",
                                 e);
    }
    return myFix.generatePreview(project, descriptorForPreview);
  }

  @Override
  public @NotNull List<@NotNull RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return myFix.getRangesToHighlight(file.getProject(), myDescriptor);
  }

  private static class ModCommandQuickFixAction implements ModCommandAction {
    private final @NotNull ProblemDescriptor myDescriptor;
    private final @NotNull ModCommandQuickFix myFix;

    private ModCommandQuickFixAction(@NotNull ProblemDescriptor descriptor, @NotNull ModCommandQuickFix fix) {
      myDescriptor = descriptor;
      myFix = fix;
    }

    @Override
    public @NotNull String getFamilyName() {
      return myFix.getName();
    }

    @Override
    public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
      PsiElement psiElement = myDescriptor.getPsiElement();
      if (psiElement == null || !psiElement.isValid()) return null;
      PsiFile containingFile = psiElement.getContainingFile();
      if (!(containingFile == context.file() || containingFile == null ||
             containingFile.getViewProvider().getVirtualFile().equals(context.file().getViewProvider().getVirtualFile()))) {
        return null;
      }
      return Presentation.of(myFix.getName())
        .withPriority(myFix instanceof PriorityAction ? ((PriorityAction)myFix).getPriority() : Priority.NORMAL)
        .withIcon(myFix instanceof Iconable ? ((Iconable)myFix).getIcon(0) : null);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      return myFix.perform(context.project(), myDescriptor);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
      return myFix.generatePreview(context.project(), myDescriptor.getDescriptorForPreview(context.file()));
    }

    @Override
    public String toString() {
      return "ModCommandQuickFixAction[fix=" + myFix + "]";
    }
  }
}
