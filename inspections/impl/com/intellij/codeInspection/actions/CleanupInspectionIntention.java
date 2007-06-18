/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class CleanupInspectionIntention implements IntentionAction {
  private LocalInspectionTool myTool;
  private Class myQuickfixClass;

  public CleanupInspectionIntention(final LocalInspectionTool tool, final int k, final ProblemDescriptor descriptor) {
    myTool = tool;
    final QuickFix[] quickFixes = descriptor.getFixes();
    if (quickFixes != null && k > -1 && k < quickFixes.length && quickFixes[k] != null) {
      myQuickfixClass = quickFixes[k].getClass();
    }
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myTool.getDisplayName());
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    runInspection(project, file, new Processor() {
      public void process(final Project project, final CommonProblemDescriptor descriptor, final QuickFix fix) {
        final PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
        if (element != null && element.isValid()) {
          fix.applyFix(project, descriptor);
        }
      }
    });
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (myQuickfixClass == null) return false;
    final int[] count = new int[]{0};
    runInspection(project, file, new Processor() {
      public void process(final Project project, final CommonProblemDescriptor descriptor, final QuickFix fix) {
        count[0] += 1;
      }
    });
    return count[0] > 1;
  }

  private void runInspection(final Project project, final PsiFile file, final Processor processor) {
    final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(project));
    final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(myTool);
    tool.initialize(context);
    ((RefManagerImpl)context.getRefManager()).inspectionReadActionStarted();
    tool.processFile(file, true, managerEx);
    final List<CommonProblemDescriptor> descriptions = new ArrayList<CommonProblemDescriptor>(tool.getProblemDescriptors());
    Collections.sort(descriptions, new Comparator<CommonProblemDescriptor>(){
      public int compare(final CommonProblemDescriptor o1, final CommonProblemDescriptor o2) {
        final ProblemDescriptorImpl d1 = (ProblemDescriptorImpl)o1;
        final ProblemDescriptorImpl d2 = (ProblemDescriptorImpl)o2;
        return d2.getTextRange().getStartOffset() - d1.getTextRange().getStartOffset();
      }
    });
    for (CommonProblemDescriptor descriptor : descriptions) {
      final QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        for (QuickFix fix : fixes) {
          if (fix != null && fix.getClass().isAssignableFrom(myQuickfixClass)) {
            processor.process(project, descriptor, fix);
            break;
          }
        }
      }
    }
    ((RefManagerImpl)context.getRefManager()).inspectionReadActionFinished();
    context.cleanup(managerEx);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static interface Processor {
    void process(Project project, CommonProblemDescriptor descriptor, QuickFix fix);
  }
}