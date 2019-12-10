// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.JBColor;
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
  private JComponent myComponent;
  private MyModePanel myModePanel;
  private LightEditStatusBar myStatusBar;

  @NotNull
  @Override
  public String ID() {
    return "light.edit.autosave";
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    assert statusBar instanceof LightEditStatusBar : "Can be added only to LightEditStatusBar";
    myStatusBar = (LightEditStatusBar)statusBar;
    myComponent = myStatusBar.getWidgetComponent(ID());
    myModePanel = new MyModePanel();
    myStatusBar.getEditorManager().addListener(this);
  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public String getTooltipText() {
    return "Autosave Mode";
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return event -> {
      if (myComponent != null) {
        myModePanel.setAutosaveSelected(LightEditService.getInstance().isAutosaveMode());
        ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myModePanel, null);
        JBPopup popup = builder.createPopup();
        popup.showInScreenCoordinates(myComponent, getPopupLocation(myComponent, myModePanel.getPreferredSize()));
      }
    };
  }

  private static Point getPopupLocation(@NotNull JComponent widgetComponent, @NotNull Dimension popupSize) {
    Point widgetLocation = widgetComponent.getVisibleRect().getLocation();
    SwingUtilities.convertPointToScreen(widgetLocation, widgetComponent);
    return new Point(widgetLocation.x, widgetLocation.y - popupSize.height);
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
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
  }
}
