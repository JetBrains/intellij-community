/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * User: anna
 */
public class IterateOverSelectedIterableIntention implements IntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) return false;
    final TemplateImpl template = getTemplate();
    if (template != null) {
      int offset = editor.getCaretModel().getOffset();
      int startOffset = offset;
      if (editor.getSelectionModel().hasSelection()) {
        final int selStart = editor.getSelectionModel().getSelectionStart();
        final int selEnd = editor.getSelectionModel().getSelectionEnd();
        startOffset = (offset == selStart) ? selEnd : selStart;
      }
      if (!template.isDeactivated() &&
          (TemplateManagerImpl.isApplicable(file, offset, template) ||
           (TemplateManagerImpl.isApplicable(file, startOffset, template)))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static TemplateImpl getTemplate() {
    return TemplateSettings.getInstance().getTemplate("I", "surround");
  }


  @NotNull
  @Override
  public String getText() {
    return "Iterate";
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final TemplateImpl template = getTemplate();
    new InvokeTemplateAction(template, editor, project, new HashSet<Character>()).perform();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }
}
