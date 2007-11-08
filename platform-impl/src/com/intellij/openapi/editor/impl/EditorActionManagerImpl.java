package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class EditorActionManagerImpl extends EditorActionManager implements ApplicationComponent {
  private TypedAction myTypedAction = new TypedAction();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentsHandler = new DefaultReadOnlyFragmentModificationHandler();
  private ActionManager myActionManager;

  public EditorActionManagerImpl(ActionManager actionManager) {
    myActionManager = actionManager;
    final BraceMatchingUtil.BraceMatcher defaultBraceMatcher = new BraceMatchingUtil.DefaultBraceMatcher();
    BraceMatchingUtil.registerBraceMatcher(StdFileTypes.JAVA,defaultBraceMatcher);
    BraceMatchingUtil.registerBraceMatcher(StdFileTypes.XML,defaultBraceMatcher);

    BraceMatchingUtil.HtmlBraceMatcher braceMatcher = new BraceMatchingUtil.HtmlBraceMatcher();
    BraceMatchingUtil.registerBraceMatcher(StdFileTypes.HTML,braceMatcher);
    BraceMatchingUtil.registerBraceMatcher(StdFileTypes.XHTML,braceMatcher);
  }

  public EditorActionHandler getActionHandler(@NotNull String actionId) {
    return ((EditorAction) myActionManager.getAction(actionId)).getHandler();
  }

  public EditorActionHandler setActionHandler(@NotNull String actionId, @NotNull EditorActionHandler handler) {
    EditorAction action = (EditorAction)myActionManager.getAction(actionId);
    return action.setupHandler(handler);
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  @NotNull
  public TypedAction getTypedAction() {
    return myTypedAction;
  }

  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentsHandler;
  }

  public ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@NotNull ReadonlyFragmentModificationHandler handler) {
    ReadonlyFragmentModificationHandler oldHandler = myReadonlyFragmentsHandler;
    myReadonlyFragmentsHandler = handler;
    return oldHandler;
  }


  @NotNull
  public String getComponentName() {
    return "EditorActionManager";
  }

  private static class DefaultReadOnlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    public void handle(ReadOnlyFragmentModificationException e) {
      Messages.showErrorDialog(EditorBundle.message("guarded.block.modification.attempt.error.message"),
                               EditorBundle.message("guarded.block.modification.attempt.error.title"));
    }
  }
}

