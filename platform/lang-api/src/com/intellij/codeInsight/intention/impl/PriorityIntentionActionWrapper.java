/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public abstract class PriorityIntentionActionWrapper implements IntentionAction {
  private final IntentionAction action;

  private PriorityIntentionActionWrapper(@NotNull IntentionAction action) {
    this.action = action;
  }

  @NotNull
  @Override
  public String getText() {
    return action.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return action.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return action.isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    action.invoke(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return action.startInWriteAction();
  }

  private static class HighPriorityIntentionActionWrapper extends PriorityIntentionActionWrapper implements HighPriorityAction {
    protected HighPriorityIntentionActionWrapper(@NotNull IntentionAction action) {
      super(action);
    }
  }

  private static class NormalPriorityIntentionActionWrapper extends PriorityIntentionActionWrapper {
    protected NormalPriorityIntentionActionWrapper(@NotNull IntentionAction action) {
      super(action);
    }
  }

  private static class LowPriorityIntentionActionWrapper extends PriorityIntentionActionWrapper implements LowPriorityAction {
    protected LowPriorityIntentionActionWrapper(@NotNull IntentionAction action) {
      super(action);
    }
  }

  @NotNull
  public static IntentionAction highPriority(@NotNull IntentionAction action) {
    return new HighPriorityIntentionActionWrapper(action);
  }

  @NotNull
  public static IntentionAction normalPriority(@NotNull IntentionAction action) {
    return new NormalPriorityIntentionActionWrapper(action);
  }

  @NotNull
  public static IntentionAction lowPriority(@NotNull IntentionAction action) {
    return new LowPriorityIntentionActionWrapper(action);
  }
}
