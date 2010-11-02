/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListScrollingUtil;

/**
 * @author yole
 */
public abstract class LookupActionHandler extends EditorActionHandler {
  protected final EditorActionHandler myOriginalHandler;
  private final boolean myRequireFocusedLookup;

  public LookupActionHandler(EditorActionHandler originalHandler, boolean requireFocusedLookup) {
    myOriginalHandler = originalHandler;
    myRequireFocusedLookup = requireFocusedLookup;
  }

  @Override
  public boolean executeInCommand(Editor editor, DataContext dataContext) {
    return LookupManager.getActiveLookup(editor) == null;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null ||
        !lookup.isVisible() && !ApplicationManager.getApplication().isUnitTestMode() ||
        myRequireFocusedLookup && !lookup.isFocused()) {
      Project project = editor.getProject();
      if (project != null) {
        LookupManager.getInstance(project).hideActiveLookup();
      }
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    lookup.markSelectionTouched();
    executeInLookup(lookup, dataContext);
  }

  protected abstract void executeInLookup(LookupImpl lookup, DataContext context);

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    return lookup != null || myOriginalHandler.isEnabled(editor, dataContext);
  }

  public static class DownHandler extends LookupActionHandler {

    public DownHandler(EditorActionHandler originalHandler){
      super(originalHandler, false);
    }

    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      if (!lookup.isFocused()) {
        lookup.setFocused(true);
        lookup.getList().setSelectedIndex(0);
        lookup.refreshUi();
      } else {
        ListScrollingUtil.moveDown(lookup.getList(), 0);
      }
    }
  }

  public static class UpHandler extends LookupActionHandler {
    public UpHandler(EditorActionHandler originalHandler){
      super(originalHandler, false);
    }

    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      if (!lookup.isFocused()) {
        if (!UISettings.getInstance().CYCLE_SCROLLING) {
          myOriginalHandler.execute(lookup.getEditor(), context);
          return;
        }

        lookup.setFocused(true);
        lookup.getList().setSelectedIndex(0);
        lookup.refreshUi();
      }
      ListScrollingUtil.moveUp(lookup.getList(), 0);
    }
  }

  public static class PageDownHandler extends LookupActionHandler {
    public PageDownHandler(final EditorActionHandler originalHandler) {
      super(originalHandler, true);
    }

    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      ListScrollingUtil.movePageDown(lookup.getList());
    }
  }

  public static class PageUpHandler extends LookupActionHandler {
    public PageUpHandler(EditorActionHandler originalHandler){
      super(originalHandler, true);
    }

    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      ListScrollingUtil.movePageUp(lookup.getList());
    }
  }

}
