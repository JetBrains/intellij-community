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

import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAwareAction;
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
      executeDown(lookup);
    }

    static void executeDown(LookupImpl lookup) {
      if (!lookup.isFocused()) {
        lookup.setFocused(true);
        lookup.getList().setSelectedIndex(0);
        lookup.refreshUi();
      } else {
        ListScrollingUtil.moveDown(lookup.getList(), 0);
      }
    }
  }

  public static class UpAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(PlatformDataKeys.EDITOR.getData(e.getDataContext()));
      assert lookup != null;
      lookup.hide();
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).actionPerformed(e);
    }

    @Override
    public void update(AnActionEvent e) {
      Lookup lookup = LookupManager.getActiveLookup(PlatformDataKeys.EDITOR.getData(e.getDataContext()));
      e.getPresentation().setEnabled(lookup != null);
    }
  }

  public static class DownAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(PlatformDataKeys.EDITOR.getData(e.getDataContext()));
      assert lookup != null;
      lookup.hide();
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN).actionPerformed(e);
    }

    @Override
    public void update(AnActionEvent e) {
      Lookup lookup = LookupManager.getActiveLookup(PlatformDataKeys.EDITOR.getData(e.getDataContext()));
      e.getPresentation().setEnabled(lookup != null);
    }
  }

  public static class UpHandler extends LookupActionHandler {
    public UpHandler(EditorActionHandler originalHandler){
      super(originalHandler, false);
    }

    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      if (!executeUp(lookup)) {
        myOriginalHandler.execute(lookup.getEditor(), context);
      }
    }

    static boolean executeUp(final LookupImpl lookup) {
      if (!lookup.isFocused()) {
        if (!UISettings.getInstance().CYCLE_SCROLLING) {
          return false;
        }

        lookup.setFocused(true);
        lookup.getList().setSelectedIndex(0);
        lookup.refreshUi();
      }
      ListScrollingUtil.moveUp(lookup.getList(), 0);
      return true;
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

  public static class LeftHandler extends LookupActionHandler {
    public LeftHandler(EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(LookupImpl lookup, DataContext context) {
      BackspaceHandler.truncatePrefix(context, lookup, myOriginalHandler, lookup.getLookupStart() - 1);
    }
  }
  public static class RightHandler extends LookupActionHandler {
    public RightHandler(EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(LookupImpl lookup, DataContext context) {
      final Editor editor = lookup.getEditor();
      final int offset = editor.getCaretModel().getOffset();
      CharSequence seq = editor.getDocument().getCharsSequence();
      if (seq.length() <= offset) {
        myOriginalHandler.execute(editor, context);
        return;
      }

      char c = seq.charAt(offset);
      CharFilter.Result lookupAction = TypedHandler.getLookupAction(c, lookup);
      if (lookupAction != CharFilter.Result.ADD_TO_PREFIX || Character.isWhitespace(c)) {
        myOriginalHandler.execute(editor, context);
        return;
      }

      lookup.performGuardedChange(new Runnable() {
        @Override
        public void run() {
          editor.getCaretModel().moveToOffset(offset + 1);
        }
      });

      lookup.appendPrefix(c);
      final CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
      if (completion != null) {
        completion.prefixUpdated();
      }
    }
  }

}
