// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class LookupActionHandler extends EditorActionHandler {
  @Nullable
  protected final EditorActionHandler myOriginalHandler;

  public LookupActionHandler(@Nullable EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public boolean executeInCommand(@NotNull Editor editor, DataContext dataContext) {
    return LookupManager.getActiveLookup(editor) == null;
  }

  @Override
  public void doExecute(@NotNull Editor editor, Caret caret, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null || !lookup.isAvailableToUser()) {
      Project project = editor.getProject();
      if (project != null && lookup != null) {
        LookupManager.getInstance(project).hideActiveLookup();
      }
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
      return;
    }

    lookup.markSelectionTouched();
    executeInLookup(lookup, dataContext, caret);
  }

  protected abstract void executeInLookup(LookupImpl lookup, DataContext context, @Nullable Caret caret);

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    return lookup != null || (myOriginalHandler != null && myOriginalHandler.isEnabled(editor, caret, dataContext));
  }

  private static void executeUpOrDown(LookupImpl lookup, boolean up) {
    if (!lookup.isFocused()) {
      boolean semiFocused = lookup.getLookupFocusDegree() == LookupFocusDegree.SEMI_FOCUSED;
      lookup.setLookupFocusDegree(LookupFocusDegree.FOCUSED);
      if (!up && !semiFocused) {
        return;
      }
    }
    if (up) {
      ScrollingUtil.moveUp(lookup.getList(), 0);
    } else {
      ScrollingUtil.moveDown(lookup.getList(), 0);
    }
    lookup.markSelectionTouched();
    lookup.refreshUi(false, true);

  }

  public static final class DownHandler extends LookupActionHandler {

    public DownHandler(EditorActionHandler originalHandler){
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context, Caret caret) {
      executeUpOrDown(lookup, false);
    }

  }

  public static final class UpAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
    public UpAction() {
      super(new UpDownInEditorHandler(true));
    }
  }

  public static final class DownAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
    public DownAction() {
      super(new UpDownInEditorHandler(false));
    }
  }

  public static final class UpInLookupAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
    public UpInLookupAction() {
      super(new UpHandler(null));
    }
  }

  public static final class DownInLookupAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
    public DownInLookupAction() {
      super(new DownHandler(null));
    }
  }

  private static final class UpDownInEditorHandler extends EditorActionHandler {
    private final boolean myUp;

    private UpDownInEditorHandler(boolean up) {
      myUp = up;
    }

    @Override
    public boolean executeInCommand(@NotNull Editor editor, DataContext dataContext) {
      return false;
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return LookupManager.getActiveLookup(editor) != null;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      assert lookup != null;
      lookup.hideLookup(true);
      EditorActionManager.getInstance().getActionHandler(myUp ? IdeActions.ACTION_EDITOR_MOVE_CARET_UP
                                                              : IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        .execute(editor, caret, dataContext);
    }
  }

  public static final class UpHandler extends LookupActionHandler {
    public UpHandler(EditorActionHandler originalHandler){
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context, Caret caret) {
      if (!UISettings.getInstance().getCycleScrolling() && !lookup.isFocused() && lookup.getList().getSelectedIndex() == 0) {
        if (myOriginalHandler != null) {
          myOriginalHandler.execute(lookup.getEditor(), caret, context);
        }
        return;
      }
      executeUpOrDown(lookup, true);
    }

  }

  public static final class PageDownHandler extends LookupActionHandler {
    public PageDownHandler(final EditorActionHandler originalHandler) {
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context, Caret caret) {
      lookup.setLookupFocusDegree(LookupFocusDegree.FOCUSED);
      ScrollingUtil.movePageDown(lookup.getList());
    }
  }

  public static final class PageUpHandler extends LookupActionHandler {
    public PageUpHandler(EditorActionHandler originalHandler){
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context, Caret caret) {
      lookup.setLookupFocusDegree(LookupFocusDegree.FOCUSED);
      ScrollingUtil.movePageUp(lookup.getList());
    }
  }

  public static class LeftHandler extends LookupActionHandler {
    public LeftHandler(@Nullable EditorActionHandler originalHandler) {
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(final LookupImpl lookup, DataContext context, Caret caret) {
      if (!lookup.isCompletion()) {
        if (myOriginalHandler != null) {
          myOriginalHandler.execute(lookup.getEditor(), caret, context);
        }
        return;
      }

      if (!lookup.performGuardedChange(() -> lookup.getEditor().getSelectionModel().removeSelection())) {
        return;
      }

      BackspaceHandler.truncatePrefix(context, lookup, myOriginalHandler, lookup.getLookupStart() - 1, caret);
    }
  }
  public static class RightHandler extends LookupActionHandler {
    public RightHandler(@Nullable EditorActionHandler originalHandler) {
      super(originalHandler);
    }

    @Override
    protected void executeInLookup(LookupImpl lookup, DataContext context, final Caret caret) {
      final Editor editor = lookup.getEditor();
      final int offset = editor.getCaretModel().getOffset();
      final CharSequence seq = editor.getDocument().getCharsSequence();
      if (seq.length() <= offset || !lookup.isCompletion()) {
        if (myOriginalHandler != null) {
          myOriginalHandler.execute(editor, caret, context);
        }
        return;
      }

      char c = seq.charAt(offset);
      CharFilter.Result lookupAction = LookupTypedHandler.getLookupAction(c, lookup);

      if (lookupAction != CharFilter.Result.ADD_TO_PREFIX || Character.isWhitespace(c)) {
        if (myOriginalHandler != null) {
          myOriginalHandler.execute(editor, caret, context);
        }
        return;
      }

      if (!lookup.performGuardedChange(() -> {
        CaretAction action = lookupCaret -> {
          lookupCaret.removeSelection();
          int caretOffset = lookupCaret.getOffset();
          if (caretOffset < seq.length()) {
            lookupCaret.moveToOffset(caretOffset + 1);
          }
        };
        if (caret == null) {
          editor.getCaretModel().runForEachCaret(action);
        }
        else {
          action.perform(caret);
        }
      })) {
        return;
      }

      lookup.fireBeforeAppendPrefix(c);
      lookup.appendPrefix(c);
      final CompletionProgressIndicator completion = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
      if (completion != null) {
        completion.prefixUpdated();
      }
    }
  }

}
