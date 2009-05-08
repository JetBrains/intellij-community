package com.intellij.ui.debugger;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.debugger.extensions.DisposerDebugger;
import com.intellij.ui.debugger.extensions.playback.PlaybackDebugger;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class UiDebugger extends JPanel implements Disposable {

  private DialogWrapper myDialog;
  private JBTabs myTabs;

  public UiDebugger() {
    Disposer.register(Disposer.get("ui"), this);

    myTabs = new JBTabsImpl(null, ActionManager.getInstance(), null, this);
    myTabs.getPresentation().setInnerInsets(new Insets(4, 0, 0, 0)).setPaintBorder(1, 0, 0, 0).setActiveTabFillIn(Color.gray).setUiDecorator(new UiDecorator() {
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(4, 4, 4, 4));
      }
    });

    final UiDebuggerExtension[] extensions = {new DisposerDebugger(), new PlaybackDebugger()};
    init(extensions);

    myDialog = new DialogWrapper((Project)null, true) {
      {
        init();
      }

      protected JComponent createCenterPanel() {
        Disposer.register(getDisposable(), UiDebugger.this);
        for (UiDebuggerExtension each : extensions) {
          Disposer.register(getDisposable(), each);
        }
        return myTabs.getComponent();
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTabs.getComponent();
      }

      @Override
      protected String getDimensionServiceKey() {
        return "UiDebugger";
      }

      @Override
      protected Action[] createActions() {
        return new Action[] {new AbstractAction("Close") {
          public void actionPerformed(ActionEvent e) {
            doOKAction();
          }
        }};
      }
    };
    myDialog.setModal(false);
    myDialog.setTitle("UI Debugger");
    myDialog.setResizable(true);

    myDialog.show();
  }

  @Override
  public void show() {
    myDialog.getPeer().getWindow().toFront();
  }

  private void init(UiDebuggerExtension[] extensions) {
    for (UiDebuggerExtension each : extensions) {
      Disposer.register(this, each);
      myTabs.addTab(new TabInfo(each.getComponent()).setText(each.getName()));
    }
  }

  public void dispose() {
  }
}