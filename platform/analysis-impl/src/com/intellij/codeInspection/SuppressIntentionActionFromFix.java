/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressIntentionActionFromFix extends SuppressIntentionAction {
  private final SuppressQuickFix myFix;

  private SuppressIntentionActionFromFix(@NotNull SuppressQuickFix fix) {
    myFix = fix;
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myFix.getElementToMakeWritable(currentFile);
  }

  @NotNull
  public static SuppressIntentionAction convertBatchToSuppressIntentionAction(@NotNull final SuppressQuickFix fix) {
    return new SuppressIntentionActionFromFix(fix);
  }

  @NotNull
  public static SuppressIntentionAction[] convertBatchToSuppressIntentionActions(@NotNull SuppressQuickFix[] actions) {
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

  @NotNull
  @Override
  public String getText() {
    return myFix.getName() + (isShouldBeAppliedToInjectionHost() == ThreeState.NO ? " in injection" : "");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myFix.getFamilyName();
  }

  @Override
  public boolean isSuppressAll() {
    return myFix.isSuppressAll();
  }
}
