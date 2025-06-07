// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.PopupHandler;
import com.intellij.util.SingleAlarm;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class DebuggerTreePanel extends UpdatableDebuggerView implements UiDataProvider, Disposable {
  public static final DataKey<DebuggerTreePanel> DATA_KEY = DataKey.create("DebuggerPanel");

  private final SingleAlarm myRebuildAlarm = new SingleAlarm(() -> {
    try {
      final DebuggerContextImpl context = getContext();
      if (context.getDebuggerSession() != null) {
        getTree().rebuild(context);
      }
    }
    catch (VMDisconnectedException ignored) {
    }
  }, 100);

  protected DebuggerTree myTree;

  public DebuggerTreePanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    myTree = createTreeView();

    final PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = createPopupMenu();
        if (popupMenu != null) {
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    };
    myTree.addMouseListener(popupHandler);

    setFocusTraversalPolicy(new IdeFocusTraversalPolicy() {
      @Override
      protected @Nullable Project getProject() {
        return project;
      }

      @Override
      public Component getDefaultComponent(Container focusCycleRoot) {
        return myTree;
      }
    });

    registerDisposable(new Disposable() {
      @Override
      public void dispose() {
        myTree.removeMouseListener(popupHandler);
      }
    });

    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.MARK_OBJECT, myTree, this);
  }

  protected abstract DebuggerTree createTreeView();

  @Override
  protected void changeEvent(DebuggerContextImpl newContext, DebuggerSession.Event event) {
    super.changeEvent(newContext, event);
    if (event == DebuggerSession.Event.DISPOSE) {
      getTree().getNodeFactory().dispose();
    }
  }

  @Override
  protected void rebuild(DebuggerSession.Event event) {
    myRebuildAlarm.cancelAndRequest();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myRebuildAlarm);
    try {
      super.dispose();
    }
    finally {
      final DebuggerTree tree = myTree;
      if (tree != null) {
        Disposer.dispose(tree);
      }
      // prevent mem leak from inside Swing
      myTree = null;
    }
  }


  protected abstract ActionPopupMenu createPopupMenu();

  public final DebuggerTree getTree() {
    return myTree;
  }

  public void clear() {
    myTree.removeAllChildren();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(DATA_KEY, this);
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getTree(), true));
  }
}
