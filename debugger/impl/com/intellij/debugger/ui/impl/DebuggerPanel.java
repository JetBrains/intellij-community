/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.PopupHandler;
import com.intellij.util.Alarm;
import com.sun.jdi.VMDisconnectedException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public abstract class DebuggerPanel extends JPanel implements DataProvider{
  private final Project myProject;
  private final DebuggerTree myTree;
  private final DebuggerStateManager myStateManager;
  private volatile boolean myRefreshNeeded = true;
  private final java.util.List<Disposable> myDisposables = new ArrayList<Disposable>();

  public DebuggerPanel(Project project, DebuggerStateManager stateManager) {
    super(new BorderLayout());
    myProject = project;
    myStateManager = stateManager;
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

    final DebuggerContextListener contextListener = new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        DebuggerPanel.this.changeEvent(newContext, event);
      }
    };
    myStateManager.addListener(contextListener);

    registerDisposable(new Disposable() {
      public void dispose() {
        myTree.removeMouseListener(popupHandler);
        myStateManager.removeListener(contextListener);
      }
    });
  }

  protected abstract DebuggerTree createTreeView();

  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if (newContext.getDebuggerSession() != null) {
      rebuildIfVisible();
    }
  }

  protected boolean isUpdateEnabled() {
    return true;
  }

  public boolean isRefreshNeeded() {
    return myRefreshNeeded;
  }

  private final Alarm myRebuildAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public final void rebuildIfVisible() {
    if(isUpdateEnabled()) {
      myRefreshNeeded = false;
      myRebuildAlarm.cancelAllRequests();
      myRebuildAlarm.addRequest(new Runnable() {
        public void run() {
          try {
            rebuild();
          }
          catch (VMDisconnectedException e) {
            // ignored
          }
        }
      }, 100);
    }
    else {
      myRefreshNeeded = true;
    }
  }

  protected void rebuild() {
    DebuggerSession debuggerSession = getContext().getDebuggerSession();
    if(debuggerSession == null) {
      return;
    }

    getTree().rebuild(getContext());
  }

  protected void showMessage(MessageDescriptor descriptor) {
    myTree.showMessage(descriptor);
  }

  protected final void registerDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  public void dispose() {
    myRebuildAlarm.dispose();
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
    myTree.dispose();
  }

  protected abstract ActionPopupMenu createPopupMenu();

  public DebuggerContextImpl getContext() {
    return myStateManager.getContext();
  }

  protected DebuggerTree getTree() {
    return myTree;
  }

  public void clear() {
    myTree.removeAllChildren();
  }

  protected final Project getProject() {
    return myProject;
  }

  public DebuggerStateManager getContextManager() {
    return myStateManager;
  }

  public Object getData(String dataId) {
    if (DebuggerActions.DEBUGGER_PANEL.equals(dataId)) {
      return this;
    }
    return null;
  }
}
