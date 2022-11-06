// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public abstract class AutoScrollToSourceHandler {
  private final Alarm myAutoScrollAlarm = new Alarm();

  public void install(final JTree tree) {
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (clickCount > 1) return false;

        TreePath location = tree.getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (location != null) {
          onMouseClicked(tree);
          // return isAutoScrollMode(); // do not consume event to allow processing by a tree
        }

        return false;
      }
    }.installOn(tree);

    tree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(final MouseEvent e) {
        onSelectionChanged(tree);
      }
    });
    tree.addTreeSelectionListener(
      new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
          onSelectionChanged(tree);
        }
      }
    );
  }

  public void install(final JTable table) {
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (clickCount >= 2) return false;

        Component location = table.getComponentAt(e.getPoint());
        if (location != null) {
          onMouseClicked(table);
          return isAutoScrollMode();
        }
        return false;
      }
    }.installOn(table);

    table.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(final MouseEvent e) {
        onSelectionChanged(table);
      }
    });
    table.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          onSelectionChanged(table);
        }
      }
    );
  }

  public void install(final JList jList) {
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (clickCount >= 2) return false;
        final Object source = e.getSource();
        final int index = jList.locationToIndex(SwingUtilities.convertPoint(source instanceof Component ? (Component)source : null, e.getPoint(), jList));
        if (index >= 0 && index < jList.getModel().getSize()) {
          onMouseClicked(jList);
          return true;
        }
        return false;
      }
    }.installOn(jList);

    jList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChanged(jList);
      }
    });
  }

  public void cancelAllRequests(){
    myAutoScrollAlarm.cancelAllRequests();
  }

  public void onMouseClicked(final Component component) {
    cancelAllRequests();
    if (isAutoScrollMode()){
      ApplicationManager.getApplication().invokeLater(() -> scrollToSource(component));
    }
  }

  private void onSelectionChanged(final Component component) {
    if (component != null && component.isShowing() && isAutoScrollMode()) {
      myAutoScrollAlarm.cancelAllRequests();
      myAutoScrollAlarm.addRequest(
        () -> {
          if (component.isShowing()) { //for tests
            if (!needToCheckFocus() || component.hasFocus()) {
              scrollToSource(component);
            }
          }
        },
        Registry.intValue("ide.autoscroll.to.source.delay", 100)
      );
    }
  }

  protected @NlsActions.ActionText String getActionName() {
    return UIBundle.message("autoscroll.to.source.action.name");
  }

  protected @NlsActions.ActionDescription String getActionDescription() {
    return UIBundle.message("autoscroll.to.source.action.description");
  }

  protected boolean needToCheckFocus(){
    return true;
  }

  protected abstract boolean isAutoScrollMode();
  protected abstract void setAutoScrollMode(boolean state);

  /**
   * @param file a file selected in a tree
   * @return {@code false} if navigation to the file is prohibited
   */
  protected boolean isAutoScrollEnabledFor(@NotNull VirtualFile file) {
    // Attempt to navigate to the virtual file with unknown file type will show a modal dialog
    // asking to register some file type for this file. This behaviour is undesirable when auto scrolling.
    FileType type = file.getFileType();
    if (type == FileTypes.UNKNOWN || type instanceof INativeFileType) return false;
    //IDEA-84881 Don't autoscroll to very large files
    return file.getLength() <= PersistentFSConstants.getMaxIntellisenseFileSize();
  }

  @RequiresEdt
  protected void scrollToSource(@NotNull Component tree) {
    AutoScrollToSourceTaskManager.getInstance()
      .scheduleScrollToSource(this,
                              DataManager.getInstance().getDataContext(tree));
  }

  @NotNull
  public ToggleAction createToggleAction() {
    return new AutoscrollToSourceAction(getActionName(), getActionDescription());
  }

  private class AutoscrollToSourceAction extends ToggleAction implements DumbAware {
    AutoscrollToSourceAction(@NlsActions.ActionText String actionName, @NlsActions.ActionDescription String actionDescription) {
      super(actionName, actionDescription, AllIcons.General.AutoscrollToSource);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return isAutoScrollMode();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      setAutoScrollMode(flag);
    }
  }
}

