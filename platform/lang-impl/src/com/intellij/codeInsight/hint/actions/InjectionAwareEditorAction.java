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
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author ignatov
 */
public class InjectionAwareEditorAction extends EditorAction {
  protected InjectionAwareEditorAction(EditorActionHandler defaultHandler) {
    super(defaultHandler);
  }

  @Nullable
  protected Editor getEditor(final DataContext dataContext) {
    Editor editor = super.getEditor(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Editor injected = project == null ? null : BaseCodeInsightAction.getInjectedEditor(project, editor);
    return injected == null ? editor : injected;
  }
}
