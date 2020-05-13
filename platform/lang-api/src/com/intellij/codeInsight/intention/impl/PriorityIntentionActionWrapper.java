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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class PriorityIntentionActionWrapper implements IntentionAction, PriorityAction {
  private final IntentionAction myAction;
  private final Priority myPriority;

  private PriorityIntentionActionWrapper(@NotNull IntentionAction action, @NotNull Priority priority) {
    myAction = action;
    myPriority = priority;
  }

  @NotNull
  @Override
  public String getText() {
    return myAction.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myAction.isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myAction.invoke(project, editor, file);
  }
  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myAction.getElementToMakeWritable(file);
  }

  @Override
  public boolean startInWriteAction() {
    return myAction.startInWriteAction();
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPriority;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    IntentionAction delegate = ObjectUtils.tryCast(myAction.getFileModifierForPreview(target), IntentionAction.class);
    return delegate == null ? null :
           delegate == myAction ? this :
           new PriorityIntentionActionWrapper(delegate, myPriority);
  }

  @NotNull
  public static IntentionAction highPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.HIGH);
  }

  @NotNull
  public static IntentionAction normalPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.NORMAL);
  }

  @NotNull
  public static IntentionAction lowPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.LOW);
  }
}
