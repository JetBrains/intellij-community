// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.*;
import com.intellij.modcommand.*;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.MathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class QuickFixWrapper implements IntentionAction, PriorityAction, CustomizableIntentionAction, ReportingClassSubstitutor,
                                              PossiblyDumbAware {
  private static final Logger LOG = Logger.getInstance(QuickFixWrapper.class);

  private final ProblemDescriptor myDescriptor;
  private final LocalQuickFix myFix;

  /**
   * Casts or wraps a <code>QuickFix</code> found by index with an <code>IntentionAction</code>.
   *
   * @return the found <code>QuickFix</code>
   * if it implements <code>IntentionAction</code>,
   * or {@link QuickFixWrapper#wrap(ProblemDescriptor, LocalQuickFix)}.
   *
   * @see QuickFixWrapper#unwrap(CommonIntentionAction)
   */
  public static @NotNull IntentionAction wrap(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    LOG.assertTrue(fixNumber >= 0, fixNumber);
    QuickFix<?>[] fixes = descriptor.getFixes();
    LOG.assertTrue(fixes != null && fixes.length > fixNumber);

    final QuickFix<?> fix = fixes[fixNumber];
    if (fix instanceof IntentionAction intention) {
      return intention;
    }
    LocalQuickFix localFix = (LocalQuickFix)fix;
    return wrap(descriptor, localFix);
  }

  /**
   * Casts or wraps a <code>LocalQuickFix</code> with an <code>IntentionAction</code>.
   *
   * @return the <code>fix</code> if it implements <code>IntentionAction</code>,
   * creates a new <code>QuickFixWrapper</code>,
   * or a mod command-specific wrapper <strong>with cached presentation</strong>.
   *
   * @see QuickFixWrapper#wrap(ProblemDescriptor, int)
   * @see QuickFixWrapper#unwrap(CommonIntentionAction)
   */
  public static @NotNull IntentionAction wrap(@NotNull ProblemDescriptor descriptor, @NotNull LocalQuickFix fix) {
    if (fix instanceof IntentionAction intention) {
      return intention;
    }
    if (fix instanceof ModCommandQuickFix modCommandFix) {
      IntentionAction intention = new ModCommandQuickFixAction(descriptor, modCommandFix).asIntention();
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      intention.isAvailable(file.getProject(), null, file); // cache presentation in wrapper
      return intention;
    }
    return new QuickFixWrapper(descriptor, fix);
  }

  /**
   * @param action action previously wrapped with {@link #wrap(ProblemDescriptor, int)} or one of the adapter classes.
   * @return a {@link LocalQuickFix} wrapped inside that action; null if the action does not contain a {@link LocalQuickFix} object
   */
  public static @Nullable LocalQuickFix unwrap(@NotNull CommonIntentionAction action) {
    if (action instanceof QuickFixWrapper wrapper) {
      return wrapper.myFix;
    }
    if (action instanceof LocalQuickFixAsIntentionAdapter adapter) {
      return adapter.getFix();
    }
    if (action instanceof LocalQuickFixOnPsiElementAsIntentionAdapter adapter) {
      return adapter.getFix();
    }
    if (action.asModCommandAction() instanceof ModCommandQuickFixAction qfAction) {
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
    if (action.asModCommandAction() instanceof ModCommandQuickFixAction qfAction) {
      PsiElement element = qfAction.myDescriptor.getPsiElement();
      return element == null ? null : element.getContainingFile();
    }
    return null;
  }

  /**
   * @see QuickFixWrapper#wrap(ProblemDescriptor, int)
   * @see QuickFixWrapper#wrap(ProblemDescriptor, LocalQuickFix)
   * @see QuickFixWrapper#unwrap(CommonIntentionAction)
   */
  private QuickFixWrapper(@NotNull ProblemDescriptor descriptor, @NotNull LocalQuickFix fix) {
    myDescriptor = descriptor;
    myFix = fix;
  }

  @Override
  public @NotNull String getText() {
    return myFix.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFix.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return false;
    PsiFile containingFile = psiElement.getContainingFile();
    return containingFile == psiFile || containingFile == null ||
           containingFile.getViewProvider().getVirtualFile().equals(psiFile.getViewProvider().getVirtualFile());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    //if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    // consider all local quick fixes do it themselves

    final PsiElement element = myDescriptor.getPsiElement();
    final PsiFile fileForUndo = element == null ? null : element.getContainingFile();
    myFix.applyFix(project, myDescriptor);
    DaemonCodeAnalyzer.getInstance(project).restart();
    if (fileForUndo != null && !fileForUndo.equals(psiFile)) {
      UndoUtil.markPsiFileForUndo(fileForUndo);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myFix.getElementToMakeWritable(file);
  }

  /**
   * @return fix wrapped by this {@link QuickFixWrapper}
   * @deprecated use {@link QuickFixWrapper#unwrap(CommonIntentionAction)} instead. Avoid {@code instanceof QuickFixWrapper} checks,
   * as the implementation may be different in the future
   */
  @Deprecated(forRemoval = true)
  public @NotNull LocalQuickFix getFix() {
    return myFix;
  }

  @Override
  public @NotNull Priority getPriority() {
    return myFix instanceof PriorityAction priority ? priority.getPriority() : Priority.NORMAL;
  }

  @TestOnly
  public static @Nullable ProblemHighlightType getHighlightType(@NotNull IntentionAction action) {
    if (action instanceof QuickFixWrapper wrapper) {
      return wrapper.myDescriptor.getHighlightType();
    }
    if (action.asModCommandAction() instanceof ModCommandQuickFixAction qfAction) {
      return qfAction.myDescriptor.getHighlightType();
    }
    return null;
  }

  public @Nullable PsiFile getFile() {
    PsiElement element = myDescriptor.getPsiElement();
    return element != null ? element.getContainingFile() : null;
  }

  @Override
  public String toString() {
    return getText();
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myFix);
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
  public @Unmodifiable @NotNull List<@NotNull RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return myFix.getRangesToHighlight(file.getProject(), myDescriptor);
  }

  private static final class ModCommandQuickFixAction implements ModCommandAction, ReportingClassSubstitutor {
    private final @NotNull ProblemDescriptor myDescriptor;
    private final @NotNull ModCommandQuickFix myFix;
    private final @Nullable ModCommandAction myUnwrappedAction;

    /**
     * @see QuickFixWrapper#wrap(ProblemDescriptor, int)
     * @see QuickFixWrapper#wrap(ProblemDescriptor, LocalQuickFix)
     * @see QuickFixWrapper#unwrap(CommonIntentionAction)
     */
    private ModCommandQuickFixAction(@NotNull ProblemDescriptor descriptor, @NotNull ModCommandQuickFix fix) {
      myDescriptor = descriptor;
      myFix = fix;
      myUnwrappedAction = ModCommandService.getInstance().unwrap(myFix);
    }

    @Override
    public @NotNull String getFamilyName() {
      return myFix.getName();
    }

    @Override
    public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
      if (myUnwrappedAction != null) {
        if (myDescriptor.getStartElement() == null) return null;
        ActionContext descriptorContext = ActionContext.from(myDescriptor);
        return myUnwrappedAction.getPresentation(
          descriptorContext.withOffset(MathUtil.clamp(context.offset(), descriptorContext.selection().getStartOffset(),
                                                      descriptorContext.selection().getEndOffset())));
      }
      PsiElement psiElement = myDescriptor.getPsiElement();
      if (psiElement == null || !psiElement.isValid()) return null;
      PsiFile containingFile = psiElement.getContainingFile();
      if (!(containingFile == context.file() || containingFile == null ||
             containingFile.getViewProvider().getVirtualFile().equals(context.file().getViewProvider().getVirtualFile()))) {
        return null;
      }
      Presentation presentation = Presentation.of(myFix.getName());
      List<RangeToHighlight> highlight = myFix.getRangesToHighlight(context.project(), myDescriptor);
      if (!highlight.isEmpty()) {
        Presentation.HighlightRange[] ranges = ContainerUtil.map2Array(highlight, Presentation.HighlightRange.class,
                                                          r -> {
                                                            return new Presentation.HighlightRange(r.getRangeInFile(),
                                                                                                   convertToHighlightingType(r.getHighlightKey()));
                                                          });
        presentation = presentation.withHighlighting(ranges);
      }
      if (myFix instanceof Iconable iconable) {
        presentation = presentation.withIcon(iconable.getIcon(0));
      }
      if (myFix instanceof PriorityAction priorityAction) {
        presentation = presentation.withPriority(priorityAction.getPriority());
      }
      return presentation;
    }

    private static @NotNull Presentation.HighlightingKind convertToHighlightingType(@NotNull TextAttributesKey key) {
      if (key == EditorColors.SEARCH_RESULT_ATTRIBUTES) {
        return Presentation.HighlightingKind.AFFECTED_RANGE;
      }
      else if (key == EditorColors.DELETED_TEXT_ATTRIBUTES) {
        return Presentation.HighlightingKind.DELETED_RANGE;
      }
      //just fallback to the default highlighting type
      return Presentation.HighlightingKind.AFFECTED_RANGE;
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
    public @NotNull Class<?> getSubstitutedClass() {
      return ReportingClassSubstitutor.getClassToReport(myFix);
    }

    @Override
    public String toString() {
      return "ModCommandQuickFixAction[fix=" + myFix + "]";
    }
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(myFix);
  }
}
