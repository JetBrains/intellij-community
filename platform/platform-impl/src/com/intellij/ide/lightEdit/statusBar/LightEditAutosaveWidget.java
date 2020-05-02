// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.ide.lightEdit.LightEditorManager;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

public class LightEditAutosaveWidget implements StatusBarWidget, StatusBarWidget.TextPresentation, LightEditorListener {
  private final LightEditorManager myLightEditorManager;
  private StatusBar myStatusBar;

  public LightEditAutosaveWidget(@NotNull LightEditorManager editorManager) {
    myLightEditorManager = editorManager;
  }

  @NotNull
  @Override
  public String ID() {
    return "light.edit.autosave";
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    myLightEditorManager.addListener(this);
    myStatusBar.updateWidget(ID());
  }

  @Override
  public void dispose() {
  }

  @NlsContexts.Tooltip
  @Nullable
  @Override
  public String getTooltipText() {
    return IdeBundle.message("tooltip.autosave.mode");
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return event -> {
      Component widgetComp = event.getComponent();
      if (widgetComp != null) {
        MyModePanel modePanel = new MyModePanel();
        modePanel.setAutosaveSelected(LightEditService.getInstance().isAutosaveMode());
        ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(modePanel, null);
        JBPopup popup = builder.createPopup();
        popup.show(new RelativePoint(widgetComp, new Point(0, -modePanel.getPreferredSize().height)));
      }
    };
  }

  @NotNull
  @Override
  public String getText() {
    return "Autosave: " + (LightEditService.getInstance().isAutosaveMode() ? "on" : "off");
  }

  @Override
  public float getAlignment() {
    return Component.LEFT_ALIGNMENT;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  private static class MyModePanel extends JPanel {
    private final JCheckBox myModeCb;

    MyModePanel() {
      setLayout(new GridBagLayout());
      setBorder(JBUI.Borders.empty(10));
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.gridx = 0;
      c.gridy = 0;
      c.gridwidth = 1;
      myModeCb = new JCheckBox();
      add(myModeCb, c);
      c.gridx = 1;
      c.gridy = 0;
      c.insets = JBUI.insetsLeft(10);
      add(new JLabel("Save changes automatically"), c);
      c.fill = GridBagConstraints.NONE;
      c.gridx = 1;
      c.gridy = 1;
      c.gridwidth = 1;
      c.insets = JBUI.insets(5, 10, 0, 0);
      final JLabel label =
        new JLabel("<html>All open files are saved on tab/window close<br>or on window deactivation.</html>");
      label.setForeground(JBColor.GRAY);
      add(label, c);

      myModeCb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LightEditService.getInstance().setAutosaveMode(myModeCb.isSelected());
        }
      });
    }


    public void setAutosaveSelected(boolean isSelected) {
      myModeCb.setSelected(isSelected);
    }
  }

  @Override
  public void autosaveModeChanged(boolean isAutosave) {
    LightEditFeatureUsagesUtil.logAutosaveModeChanged(isAutosave);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
  }
}
