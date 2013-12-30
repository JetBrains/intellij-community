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
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ApplyIntentionAction extends AnAction {

  private final HighlightInfo.IntentionActionDescriptor myDescriptor;
  private final Editor myEditor;
  private final PsiFile myFile;

  public ApplyIntentionAction(HighlightInfo.IntentionActionDescriptor descriptor, Editor editor, PsiFile file) {
    super(descriptor.getAction().getText());
    myDescriptor = descriptor;
    myEditor = editor;
    myFile = file;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IntentionAction action = myDescriptor.getAction();
    ShowIntentionActionsHandler.chooseActionAndInvoke(myFile, myEditor, action, action.getText());
  }

  public String getName() {
    return myDescriptor.getAction().getText();
  }

  @Nullable
  public static ApplyIntentionAction[] getAvailableIntentions(final Editor editor, final PsiFile file) {
    final ShowIntentionsPass.IntentionsInfo info = new ShowIntentionsPass.IntentionsInfo();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        ShowIntentionsPass.getActionsToShow(editor, file, info, -1);
      }
    });
    if (info.isEmpty()) return null;

    final List<HighlightInfo.IntentionActionDescriptor> actions = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    actions.addAll(info.errorFixesToShow);
    actions.addAll(info.inspectionFixesToShow);
    actions.addAll(info.intentionsToShow);

    final ApplyIntentionAction[] result = new ApplyIntentionAction[actions.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = new ApplyIntentionAction(actions.get(i), editor, file);
    }
    return result;
  }
}
