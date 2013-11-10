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
package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;

/**
 * @author peter
 */
public class ExpandLiveTemplateByTabAction extends EditorAction {
  public ExpandLiveTemplateByTabAction() {
    super(new EditorWriteActionHandler() {
      @Override
      public void executeWriteAction(Editor editor, DataContext dataContext) {
        Project project = editor.getProject();
        TemplateManager.getInstance(project).startTemplate(editor, TemplateSettings.TAB_CHAR);
      }

      @Override
      public boolean isEnabled(Editor editor, DataContext dataContext) {
        Project project = editor.getProject();
        return project != null &&
               ((TemplateManagerImpl)TemplateManager.getInstance(project)).prepareTemplate(editor, TemplateSettings.TAB_CHAR, null) != null;
      }
    });
    setInjectedContext(true);
  }
}
