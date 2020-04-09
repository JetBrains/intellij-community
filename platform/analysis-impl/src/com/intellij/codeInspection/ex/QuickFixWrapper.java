// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class QuickFixWrapper implements IntentionAction, PriorityAction {
  private static final Logger LOG = Logger.getInstance(QuickFixWrapper.class);

  private final ProblemDescriptor myDescriptor;
  private final LocalQuickFix myFix;

  @NotNull
  public static IntentionAction wrap(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    LOG.assertTrue(fixNumber >= 0, fixNumber);
    QuickFix[] fixes = descriptor.getFixes();
    LOG.assertTrue(fixes != null && fixes.length > fixNumber);

    final QuickFix fix = fixes[fixNumber];
    return fix instanceof IntentionAction ? (IntentionAction)fix : new QuickFixWrapper(descriptor, (LocalQuickFix)fix);
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
    return getFix().getName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    //if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    // consider all local quick fixes do it themselves

    final PsiElement element = myDescriptor.getPsiElement();
    final PsiFile fileForUndo = element == null ? null : element.getContainingFile();
    LocalQuickFix fix = getFix();
    fix.applyFix(project, myDescriptor);
    DaemonCodeAnalyzer.getInstance(project).restart();
    if (fileForUndo != null && !fileForUndo.equals(file)) {
      UndoUtil.markPsiFileForUndo(fileForUndo);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return getFix().startInWriteAction();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return getFix().getElementToMakeWritable(file);
  }

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
  public ProblemHighlightType getHighlightType() {
    return myDescriptor.getHighlightType();
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
  public @Nullable IntentionAction getFileModifierForPreview(@NotNull PsiFile target) {
    LocalQuickFix result = ObjectUtils.tryCast(myFix.getFileModifierForPreview(target), LocalQuickFix.class);
    if (result == null) return null;
    PsiElement start, end, psi;
    try {
      start = findSameElementInCopy(myDescriptor.getStartElement(), target);
      end = findSameElementInCopy(myDescriptor.getEndElement(), target);
      psi = findSameElementInCopy(myDescriptor.getPsiElement(), target);
    }
    catch (IllegalStateException e) {
      return null;
    }
    ProblemDescriptor descriptor = new ProblemDescriptor() {
      //@formatter:off
      @Override public PsiElement getPsiElement() { return psi;}
      @Override public PsiElement getStartElement() { return start;}
      @Override public PsiElement getEndElement() { return end;}
      @Override public TextRange getTextRangeInElement() { return myDescriptor.getTextRangeInElement();}
      @Override public int getLineNumber() { return myDescriptor.getLineNumber();}
      @Override public @NotNull ProblemHighlightType getHighlightType() { return myDescriptor.getHighlightType();}
      @Override public boolean isAfterEndOfLine() { return myDescriptor.isAfterEndOfLine();}
      @Override public void setTextAttributes(TextAttributesKey key) {}
      @Override public @Nullable ProblemGroup getProblemGroup() { return myDescriptor.getProblemGroup(); }
      @Override public void setProblemGroup(@Nullable ProblemGroup problemGroup) {}
      @Override public boolean showTooltip() { return myDescriptor.showTooltip();}
      @Override public @NotNull String getDescriptionTemplate() { return myDescriptor.getDescriptionTemplate();}
      @Override public QuickFix<?> @Nullable [] getFixes() { return QuickFix.EMPTY_ARRAY;}
      //@formatter:on
    };
    return new QuickFixWrapper(descriptor, result);
  }

  /**
   * Returns the same element in the file copy.
   * 
   * @param element an element to find
   * @param copy file that must be a copy of {@code element.getContainingFile()}
   * @return found element; null if input element is null
   * @throws IllegalStateException if it's detected that the supplied file is not exact copy of original file. 
   * The exception is thrown on a best-effort basis, so you cannot rely on it. 
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static <T extends PsiElement> T findSameElementInCopy(@Nullable T element, @NotNull PsiFile copy) throws IllegalStateException {
    if (element == null) return null;
    TextRange range = element.getTextRange();
    PsiElement newElement = copy.findElementAt(range.getStartOffset());
    while (newElement != null) {
      TextRange newRange = newElement.getTextRange();
      if (newRange.equals(range) && newElement.getClass().equals(element.getClass())) {
        //noinspection unchecked
        return (T)newElement;
      }
      if (newRange.getStartOffset() < range.getStartOffset() || newRange.getEndOffset() > range.getEndOffset()) break;
      newElement = newElement.getParent();
    }
    throw new IllegalStateException("Cannot find element in copy file");
  }
}
