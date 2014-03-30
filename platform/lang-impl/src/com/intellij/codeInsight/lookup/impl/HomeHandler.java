/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

public class HomeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public HomeHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(Editor editor, Caret caret, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null || !lookup.isFocused()) {
      myOriginalHandler.execute(editor, caret, dataContext);
      return;
    }

    lookup.markSelectionTouched();
    ListScrollingUtil.moveHome(lookup.getList());
  }
}
