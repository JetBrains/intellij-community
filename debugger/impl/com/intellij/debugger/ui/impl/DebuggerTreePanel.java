/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class DebuggerTreePanel extends UpdatableDebuggerView implements DataProvider {
  protected final DebuggerTree myTree;

  public DebuggerTreePanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    myTree = createTreeView();

    final PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = createPopupMenu();
        if (popupMenu != null) {
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    };
    myTree.addMouseListener(popupHandler);

    setFocusTraversalPolicy(new IdeFocusTraversalPolicy() {
      public Component getDefaultComponentImpl(Container focusCycleRoot) {
        return myTree;
      }
    });

    registerDisposable(new Disposable() {
      public void dispose() {
        myTree.removeMouseListener(popupHandler);
      }
    });

    overrideShortcut(myTree, DebuggerActions.MARK_OBJECT, KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
  }

  protected abstract DebuggerTree createTreeView();


  protected void rebuild(final boolean updateOnly) {
    DebuggerSession debuggerSession = getContext().getDebuggerSession();
    if(debuggerSession == null) {
      return;
    }

    getTree().rebuild(getContext());
  }

  public void dispose() {
    super.dispose();
    myTree.dispose();
  }


  protected abstract ActionPopupMenu createPopupMenu();

  public final DebuggerTree getTree() {
    return myTree;
  }

  public void clear() {
    myTree.removeAllChildren();
  }

  public Object getData(String dataId) {
    if (DebuggerActions.DEBUGGER_PANEL.equals(dataId)) {
      return this;
    }
    return null;
  }

  public void requestFocus() {
    getTree().requestFocus();
  }
}
