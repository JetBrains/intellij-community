/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.PerformFixesModalTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final static Logger LOG = Logger.getInstance(CleanupInspectionIntention.class);

  private final InspectionToolWrapper myToolWrapper;
  private final Class myQuickfixClass;
  private final String myText;

  public CleanupInspectionIntention(@NotNull InspectionToolWrapper toolWrapper, @NotNull Class quickFixClass, String text) {
    myToolWrapper = toolWrapper;
    myQuickfixClass = quickFixClass;
    myText = text;
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myToolWrapper.getDisplayName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {

    final List<ProblemDescriptor> descriptions =
      ProgressManager.getInstance().runProcess(() -> {
        InspectionManager inspectionManager = InspectionManager.getInstance(project);
        return InspectionEngine.runInspectionOnFile(file, myToolWrapper, inspectionManager.createNewGlobalContext(false));
      }, new EmptyProgressIndicator());

    if (!descriptions.isEmpty() && !FileModificationService.getInstance().preparePsiElementForWrite(file)) return;

    final AbstractPerformFixesTask fixesTask = applyFixes(project, "Apply Fixes", descriptions, myQuickfixClass);

    if (!fixesTask.isApplicableFixFound()) {
      HintManager.getInstance().showErrorHint(editor, "Unfortunately '" + myText + "' is currently not available for batch mode\n User interaction is required for each problem found");
    }
  }

  public static AbstractPerformFixesTask applyFixes(@NotNull Project project,
                                                    @NotNull String presentationText,
                                                    @NotNull List<ProblemDescriptor> descriptions,
                                                    @Nullable Class quickfixClass) {
    sortDescriptions(descriptions);
    return applyFixesNoSort(project, presentationText, descriptions, quickfixClass);
  }

  public static AbstractPerformFixesTask applyFixesNoSort(@NotNull Project project,
                                                          @NotNull String presentationText,
                                                          @NotNull List<ProblemDescriptor> descriptions,
                                                          @Nullable Class quickfixClass) {

    final boolean isBatch = quickfixClass != null && BatchQuickFix.class.isAssignableFrom(quickfixClass);
    final AbstractPerformFixesTask fixesTask = isBatch ?
                                               new PerformBatchFixesTask(project, descriptions.toArray(ProblemDescriptor.EMPTY_ARRAY), quickfixClass) :
                                               new PerformFixesTask(project, descriptions.toArray(ProblemDescriptor.EMPTY_ARRAY), quickfixClass);
    CommandProcessor.getInstance().executeCommand(project, () -> {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
      ((ApplicationImpl)ApplicationManager.getApplication())
        .runWriteActionWithProgressInDispatchThread(presentationText, project, null, null, fixesTask::doRun);
    }, presentationText, null);
    return fixesTask;
  }

  public static void sortDescriptions(@NotNull List<ProblemDescriptor> descriptions) {
    Collections.sort(descriptions, CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myQuickfixClass != EmptyIntentionAction.class &&
           editor != null &&
           !(myToolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)myToolWrapper).isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static abstract class AbstractPerformFixesTask extends PerformFixesModalTask {
    private boolean myApplicableFixFound = false;
    protected final Class myQuickfixClass;

    public AbstractPerformFixesTask(@NotNull Project project,
                                    @NotNull CommonProblemDescriptor[] descriptors,
                                    @Nullable Class quickfixClass) {
      super(project, descriptors);
      myQuickfixClass = quickfixClass;
    }

    protected abstract void collectFix(QuickFix fix, ProblemDescriptor descriptor, Project project);

    @Override
    protected final void applyFix(Project project, CommonProblemDescriptor descriptor) {
      final QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        for (final QuickFix fix : fixes) {
          if (fix != null && (myQuickfixClass == null || fix.getClass().isAssignableFrom(myQuickfixClass))) {
            final ProblemDescriptor problemDescriptor = (ProblemDescriptor)descriptor;
            final PsiElement element = problemDescriptor.getPsiElement();
            if (element != null && element.isValid()) {
              collectFix(fix, problemDescriptor, project);
              myApplicableFixFound = true;
            }
            break;
          }
        }
      }
    }

    public final boolean isApplicableFixFound() {
      return myApplicableFixFound;
    }
  }

  private static class PerformBatchFixesTask extends AbstractPerformFixesTask {
    private final List<ProblemDescriptor> myBatchModeDescriptors = new ArrayList<>();
    private boolean myApplied = false;

    public PerformBatchFixesTask(@NotNull Project project,
                                 @NotNull CommonProblemDescriptor[] descriptors,
                                 @NotNull Class quickfixClass) {
      super(project, descriptors, quickfixClass);
    }

    @Override
    protected void collectFix(QuickFix fix, ProblemDescriptor descriptor, Project project) {
      myBatchModeDescriptors.add(descriptor);
    }

    @Override
    public boolean isDone() {
      if (super.isDone()) {
        if (!myApplied && !myBatchModeDescriptors.isEmpty()) {
          final ProblemDescriptor representative = myBatchModeDescriptors.get(0);
          LOG.assertTrue(representative.getFixes() != null);
          for (QuickFix fix : representative.getFixes()) {
            if (fix != null && fix.getClass().isAssignableFrom(myQuickfixClass)) {
              ((BatchQuickFix)fix).applyFix(myProject,
                                            myBatchModeDescriptors.toArray(new ProblemDescriptor[myBatchModeDescriptors.size()]),
                                            new ArrayList<>(),
                                            null);
              break;
            }
          }
          myApplied = true;
        }
        return true;
      }
      else {
        return false;
      }
    }
  }
  
  private static class PerformFixesTask extends AbstractPerformFixesTask {
    public PerformFixesTask(@NotNull Project project,
                            @NotNull CommonProblemDescriptor[] descriptors,
                            @Nullable Class quickFixClass) {
      super(project, descriptors, quickFixClass);
    }

    @Override
    protected void collectFix(QuickFix fix, ProblemDescriptor descriptor, Project project) {
      fix.applyFix(project, descriptor);
    }
  }
}
