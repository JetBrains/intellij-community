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
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final static Logger LOG = Logger.getInstance(CleanupInspectionIntention.class);

  private final InspectionToolWrapper myToolWrapper;
  private final FileModifier myQuickfix;
  private final String myText;

  public CleanupInspectionIntention(@NotNull InspectionToolWrapper toolWrapper, @NotNull FileModifier quickFix, String text) {
    myToolWrapper = toolWrapper;
    myQuickfix = quickFix;
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

    final AbstractPerformFixesTask fixesTask = CleanupInspectionUtil.getInstance().applyFixes(project, "Apply Fixes", descriptions, myQuickfix.getClass(), myQuickfix.startInWriteAction());

    if (!fixesTask.isApplicableFixFound()) {
      HintManager.getInstance().showErrorHint(editor, "Unfortunately '" + myText + "' is currently not available for batch mode\n User interaction is required for each problem found");
    }
  }

  /**
   * @deprecated Use {@link CleanupInspectionUtil} instead
   */
  @Deprecated
  public static AbstractPerformFixesTask applyFixes(@NotNull Project project,
                                                    @NotNull String presentationText,
                                                    @NotNull List<ProblemDescriptor> descriptions,
                                                    @Nullable Class quickfixClass,
                                                             boolean startInWriteAction) {
    return CleanupInspectionUtil.getInstance()
        .applyFixes(project, presentationText, descriptions, quickfixClass, startInWriteAction);
  }

  /**
   * @deprecated Use {@link CleanupInspectionUtil} instead
   */
  @Deprecated
  public static AbstractPerformFixesTask applyFixesNoSort(@NotNull Project project,
                                                          @NotNull String presentationText,
                                                          @NotNull List<ProblemDescriptor> descriptions,
                                                          @Nullable Class quickfixClass,
                                                                    boolean startInWriteAction) {
    return CleanupInspectionUtil.getInstance()
        .applyFixesNoSort(project, presentationText, descriptions, quickfixClass, startInWriteAction);
  }

  /**
   * @deprecated Use {@link CleanupInspectionUtil} instead
   */
  @Deprecated
  public static void sortDescriptions(@NotNull List<ProblemDescriptor> descriptions) {
    Collections.sort(descriptions, CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myQuickfix.getClass() != EmptyIntentionAction.class &&
           editor != null &&
           !(myToolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)myToolWrapper).isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
