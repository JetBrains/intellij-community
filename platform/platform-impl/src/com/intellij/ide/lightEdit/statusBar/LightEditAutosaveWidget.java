// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.ide.lightEdit.LightEditorManager;
import com.intellij.openapi.application.ApplicationBundle;
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

public final class LightEditAutosaveWidget implements StatusBarWidget, StatusBarWidget.TextPresentation, LightEditorListener {
  private final LightEditorManager myLightEditorManager;
  private StatusBar myStatusBar;

  public LightEditAutosaveWidget(@NotNull LightEditorManager editorManager) {
    myLightEditorManager = editorManager;
  }

  @Override
  public @NotNull String ID() {
    return "light.edit.autosave";
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    myLightEditorManager.addListener(this);
    myStatusBar.updateWidget(ID());
  }

  @Override
  public @NlsContexts.Tooltip @Nullable String getTooltipText() {
    return IdeBundle.message("tooltip.autosave.mode");
  }

  @Override
  public @Nullable Consumer<MouseEvent> getClickConsumer() {
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

  @Override
  public @NotNull String getText() {
    return ApplicationBundle.message("light.edit.autosave.widget.text",
                                     (LightEditService.getInstance().isAutosaveMode() ?
                                      ApplicationBundle.message("light.edit.autosave.widget.on") :
                                      ApplicationBundle.message("light.edit.autosave.widget.off")));
  }

  @Override
  public float getAlignment() {
    return Component.LEFT_ALIGNMENT;
  }

  @Override
  public @Nullable WidgetPresentation getPresentation() {
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
      add(new JLabel(ApplicationBundle.message("light.edit.autosave.widget.popup.title")), c);
      c.fill = GridBagConstraints.NONE;
      c.gridx = 1;
      c.gridy = 1;
      c.gridwidth = 1;
      c.insets = JBUI.insets(5, 10, 0, 0);
      final JLabel label =
        new JLabel(getPopupText());
      label.setForeground(JBColor.GRAY);
      add(label, c);

      myModeCb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LightEditService.getInstance().setAutosaveMode(myModeCb.isSelected());
        }
      });
    }

    private static @NlsContexts.PopupContent String getPopupText() {
      StringBuilder builder = new StringBuilder();
      GeneralSettings generalSettings = GeneralSettings.getInstance();
      if (generalSettings.isAutoSaveIfInactive()) {
        builder.append(ApplicationBundle.message("light.edit.autosave.widget.popup.text.time", generalSettings.getInactiveTimeout()));
      }
      if (generalSettings.isSaveOnFrameDeactivation()) {
        if (builder.length() > 0) {
          builder.append(",<br>");
        }
        String message = ApplicationBundle.message("light.edit.autosave.widget.popup.text.deactivation");
        if (builder.length() > 0) {
          message = message.replaceAll("<br>", " ");
        }
        builder.append(message);
      }
      if (builder.length() == 0) {
        builder.append(ApplicationBundle.message("light.edit.autosave.widget.popup.text.on.close"));
      }
      return ApplicationBundle.message("light.edit.autosave.widget.popup.text", builder.toString());
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
