// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
class DynamicEditorActionHandler extends EditorActionHandler {
  private static final List<EditorActionHandler> UPDATE_MARKER = new ArrayList<>();

  private final EditorAction myAction;
  private final EditorActionHandler myBaseHandler;
  private final AtomicReference<List<EditorActionHandler>> myCachedChain = new AtomicReference<>();
  private boolean myWorksInInjected;

  DynamicEditorActionHandler(@NotNull EditorAction editorAction, @NotNull EditorActionHandler baseHandler) {
    myAction = editorAction;
    myBaseHandler = baseHandler;
  }

  @Override
  public boolean runForAllCarets() {
    return getHandler().runForAllCarets();
  }

  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return getHandler().isEnabled(editor, dataContext);
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return getHandler().isEnabledForCaret(editor, caret, dataContext);
  }

  @Override
  public void execute(@NotNull Editor editor, @Nullable DataContext dataContext) {
    getHandler().execute(editor, dataContext);
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    getHandler().doExecute(editor, caret, dataContext);
  }

  @Override
  public boolean executeInCommand(@NotNull Editor editor, DataContext dataContext) {
    return getHandler().executeInCommand(editor, dataContext);
  }

  @Override
  public DocCommandGroupId getCommandGroupId(@NotNull Editor editor) {
    return getHandler().getCommandGroupId(editor);
  }

  @Override
  synchronized void setWorksInInjected(boolean worksInInjected) {
    super.setWorksInInjected(worksInInjected);
    myWorksInInjected = worksInInjected;
    clearCache();
  }

  @Override
  <T> @Nullable T getHandlerOfType(@NotNull Class<T> type) {
    List<EditorActionHandler> chain = getHandlerChain();
    for (int i = chain.size() - 1; i >= 0; i--) {
      T handler = chain.get(i).getHandlerOfType(type);
      if (handler != null) return handler;
    }
    return null;
  }

  private EditorActionHandler getHandler() {
    return ContainerUtil.getLastItem(getHandlerChain());
  }

  private synchronized List<EditorActionHandler> getHandlerChain() {
    List<EditorActionHandler> cachedChain = myCachedChain.get();
    if (cachedChain != null && cachedChain != UPDATE_MARKER) return cachedChain;
    myCachedChain.set(UPDATE_MARKER);
    List<EditorActionHandlerBean> handlerBeans = ActionManagerEx.getInstanceEx().getRegisteredHandlers(myAction);
    List<EditorActionHandler> chain = new ArrayList<>(handlerBeans.size());
    chain.add(myBaseHandler);
    for (EditorActionHandlerBean handlerBean : handlerBeans) {
      EditorActionHandler handler = handlerBean.getHandler(ContainerUtil.getLastItem(chain));
      if (handler != null) {
        handler.setWorksInInjected(myWorksInInjected);
        chain.add(handler);
      }
    }
    // We want to avoid deadlock with ActionManagerImpl, so 'clearCache' method isn't synchronized.
    // That's why we may cache the result only if ActionManagerImpl hasn't invalidated registered handlers
    // while we were calculating it.
    myCachedChain.compareAndSet(UPDATE_MARKER, chain);
    return chain;
  }

  void clearCache() {
    myCachedChain.set(null);
  }
}
