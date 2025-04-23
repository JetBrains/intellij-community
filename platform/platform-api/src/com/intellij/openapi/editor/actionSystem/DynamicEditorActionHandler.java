// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class DynamicEditorActionHandler extends EditorActionHandler {
  private static final List<EditorActionHandler> UPDATE_MARKER = new ArrayList<>();
  private static final ExtensionPointName<EditorActionHandlerBean> EDITOR_ACTION_HANDLER_EP =
    new ExtensionPointName<>("com.intellij.editorActionHandler");

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
  protected boolean reverseCaretOrder() {
    return getHandler().reverseCaretOrder();
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return getHandler().isEnabled(editor, dataContext);
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return getHandler().isEnabledForCaret(editor, caret, dataContext);
  }

  @SuppressWarnings("deprecation")
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
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }

  private @Nullable EditorActionHandler getHandler() {
    List<EditorActionHandler> list = getHandlerChain();
    return list.isEmpty() ? null : list.get(list.size() - 1);
  }

  private synchronized @NotNull List<EditorActionHandler> getHandlerChain() {
    List<EditorActionHandler> cachedChain = myCachedChain.get();
    if (cachedChain != null && cachedChain != UPDATE_MARKER) {
      return cachedChain;
    }

    myCachedChain.set(UPDATE_MARKER);
    List<EditorActionHandlerBean> handlerBeans = getRegisteredHandlers(myAction);
    List<EditorActionHandler> chain = new ArrayList<>(handlerBeans.size());
    chain.add(myBaseHandler);
    Application container = ApplicationManager.getApplication();
    for (EditorActionHandlerBean bean : handlerBeans) {
      EditorActionHandler handler;
      EditorActionHandler param = chain.isEmpty() ? null : chain.get(chain.size() - 1);
      try {
        Class<EditorActionHandler> beanImplementationClass = container.loadClass(bean.implementationClass, bean.pluginDescriptor);
        Constructor<EditorActionHandler> constructor;
        try {
          constructor = beanImplementationClass.getDeclaredConstructor(EditorActionHandler.class);
        }
        catch (NoSuchMethodException ignore) {
          constructor = null;
        }

        if (constructor == null) {
          constructor = beanImplementationClass.getDeclaredConstructor();
          constructor.setAccessible(true);
          handler = constructor.newInstance();
        }
        else {
          constructor.setAccessible(true);
          handler = constructor.newInstance(param);
        }
      }
      catch (Exception e) {
        if (ExceptionUtil.causedBy(e, ExtensionNotApplicableException.class)) continue;
        Logger.getInstance(EditorActionHandlerBean.class).error(new PluginException(e, bean.pluginDescriptor.getPluginId()));
        continue;
      }

      handler.setWorksInInjected(myWorksInInjected);
      chain.add(handler);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Updated DynamicEditorActionHandler chain for " + myAction.getClass() + ": " + chain,
                LOG.isTraceEnabled() ? new Throwable() : null);
    }

    // We want to avoid deadlock with ActionManagerImpl, so 'clearCache' method isn't synchronized.
    // That's why we may cache the result only if ActionManagerImpl hasn't invalidated registered handlers
    // while we were calculating it.
    myCachedChain.compareAndSet(UPDATE_MARKER, chain);
    return chain;
  }

  private static @NotNull List<EditorActionHandlerBean> getRegisteredHandlers(@NotNull EditorAction editorAction) {
    String id = ActionManager.getInstance().getId(editorAction);
    if (id == null) {
      return Collections.emptyList();
    }

    List<EditorActionHandlerBean> extensions = EDITOR_ACTION_HANDLER_EP.getExtensionList();
    List<EditorActionHandlerBean> result = new ArrayList<>();
    for (int i = extensions.size() - 1; i >= 0; i--) {
      EditorActionHandlerBean handlerBean = extensions.get(i);
      if (handlerBean.action.equals(id)) {
        result.add(handlerBean);
      }
    }
    return result;
  }

  void clearCache() {
    myCachedChain.set(null);
  }

  @Override
  public String toString() {
    return "DynamicEditorActionHandler{" +
           "myAction=" + myAction.getClass() +
           ", myBaseHandler=" + myBaseHandler +
           '}';
  }
}
