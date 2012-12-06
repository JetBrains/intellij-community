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

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
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

  @Override
  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null || !lookup.isAvailableToUser() || myRequireFocusedLookup && !lookup.isFocused()) {
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

  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    return lookup != null || myOriginalHandler.isEnabled(editor, dataContext);
  }

  public static class DownHandler extends LookupActionHandler {

    public DownHandler(EditorActionHandler originalHandler){
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      executeDown(lookup);
    }

    static void executeDown(LookupImpl lookup) {
      if (!lookup.isFocused()) {
        lookup.setFocused(true);
        lookup.uninstallPreview();
        lookup.getList().setSelectedIndex(0);
        lookup.refreshUi(false, true);
      } else {
        ListScrollingUtil.moveDown(lookup.getList(), 0);
      }
    }
  }

  public static class UpAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ARROWS);
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
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ARROWS);
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

    @Override
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
        lookup.uninstallPreview();
        lookup.getList().setSelectedIndex(0);
      }
      lookup.markSelectionTouched();
      ListScrollingUtil.moveUp(lookup.getList(), 0);
      lookup.refreshUi(false, true);
      return true;
    }
  }

  public static class PageDownHandler extends LookupActionHandler {
    public PageDownHandler(final EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      lookup.setFocused(true);
      lookup.uninstallPreview();
      ListScrollingUtil.movePageDown(lookup.getList());
    }
  }

  public static class PageUpHandler extends LookupActionHandler {
    public PageUpHandler(EditorActionHandler originalHandler){
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      lookup.setFocused(true);
      lookup.uninstallPreview();
      ListScrollingUtil.movePageUp(lookup.getList());
    }
  }

  public static class LeftHandler extends LookupActionHandler {
    public LeftHandler(EditorActionHandler originalHandler) {
      super(originalHandler, false);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context) {
      if (!lookup.isCompletion()) {
        myOriginalHandler.execute(lookup.getEditor(), context);
        return;
      }

      CompletionPreview preview = lookup.uninstallPreview();

      if (!lookup.performGuardedChange(new Runnable() {
        @Override
        public void run() {
          lookup.getEditor().getSelectionModel().removeSelection();
        }
      })) {
        return;
      }

      BackspaceHandler.truncatePrefix(context, lookup, myOriginalHandler, lookup.getLookupStart() - 1);

      CompletionPreview.reinstallPreview(preview);
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
      if (seq.length() <= offset || !lookup.isCompletion()) {
        myOriginalHandler.execute(editor, context);
        return;
      }

      char c = seq.charAt(offset);
      CharFilter.Result lookupAction = LookupTypedHandler.getLookupAction(c, lookup);

      if (lookupAction != CharFilter.Result.ADD_TO_PREFIX || Character.isWhitespace(c)) {
        myOriginalHandler.execute(editor, context);
        return;
      }

      CompletionPreview preview = lookup.uninstallPreview();

      if (!lookup.performGuardedChange(new Runnable() {
        @Override
        public void run() {
          editor.getSelectionModel().removeSelection();
          editor.getCaretModel().moveToOffset(offset + 1);
        }
      })) {
        return;
      }

      lookup.appendPrefix(c);
      final CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
      if (completion != null) {
        completion.prefixUpdated();
      }
      CompletionPreview.reinstallPreview(preview);
    }
  }

}
