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
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * User: anna
 * Date: 12/27/11
 */
public class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void execute(Editor editor, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      final TemplateState state = TemplateManagerImpl.getTemplateState(editor);
      if (state != null && editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) != null) {
        final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
        if (lookup != null) {
          selectionModel.removeSelection();
          lookup.hide();
          return;
        }
      }
    }

    myOriginalHandler.execute(editor, dataContext);
  }

  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      return true;
    } else {
      return myOriginalHandler.isEnabled(editor, dataContext);
    }
  }
}
