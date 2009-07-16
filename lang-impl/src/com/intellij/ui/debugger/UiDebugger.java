package com.intellij.ui.debugger;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;

public class UiDebugger extends JPanel implements Disposable {

  private DialogWrapper myDialog;
  private JBTabs myTabs;
  private UiDebuggerExtension[] myExtensions;

  public UiDebugger() {
    Disposer.register(Disposer.get("ui"), this);

    myTabs = new JBTabsImpl(null, ActionManager.getInstance(), null, this);
    myTabs.getPresentation().setInnerInsets(new Insets(4, 0, 0, 0)).setPaintBorder(1, 0, 0, 0).setActiveTabFillIn(Color.gray).setUiDecorator(new UiDecorator() {
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(4, 4, 4, 4));
      }
    });

    myExtensions = Extensions.getExtensions(UiDebuggerExtension.EP_NAME);
    addToUi(myExtensions);

    myDialog = new DialogWrapper((Project)null, true) {
      {
        init();
      }

      protected JComponent createCenterPanel() {
        Disposer.register(getDisposable(), UiDebugger.this);
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
      protected JComponent createSouthPanel() {
        final JPanel result = new JPanel(new BorderLayout());
        result.add(super.createSouthPanel(), BorderLayout.EAST);
        final JSlider slider = new JSlider(0, 100);
        slider.setValue(100);
        slider.addChangeListener(new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            final int value = slider.getValue();
            float alpha = value / 100f;

            final Window wnd = SwingUtilities.getWindowAncestor(slider);
            if (wnd != null) {
              final WindowManagerEx mgr = WindowManagerEx.getInstanceEx();
              if (value == 100) {
                mgr.setAlphaModeEnabled(wnd, false);
              } else {
                mgr.setAlphaModeEnabled(wnd, true);
                mgr.setAlphaModeRatio(wnd, 1f - alpha);
              }
            }
          }
        });
        result.add(slider, BorderLayout.WEST);
        return result;
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

  private void addToUi(UiDebuggerExtension[] extensions) {
    for (UiDebuggerExtension each : extensions) {
      myTabs.addTab(new TabInfo(each.getComponent()).setText(each.getName()));
    }
  }

  public void dispose() {
    for (UiDebuggerExtension each : myExtensions) {
      each.disposeUiResources();
    }
  }
}