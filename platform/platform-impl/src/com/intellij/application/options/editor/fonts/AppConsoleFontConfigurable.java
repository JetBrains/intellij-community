// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.AppConsoleFontOptions;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.AppFontOptions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class AppConsoleFontConfigurable extends AppFontConfigurable {

  public static final String ID = "app.console.font";

  private ConsoleFontOptionsPanel myFontOptionsPanel;

  @Override
  public String getDisplayName() {
    return IdeBundle.message("configurable.console.font.name");
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  protected @NotNull AppFontOptions<?> getFontOptions() {
    return AppConsoleFontOptions.getInstance();
  }

  @Override
  protected @NotNull AppFontOptionsPanel createFontOptionsPanel(@NotNull EditorColorsScheme previewScheme) {
    myFontOptionsPanel = new ConsoleFontOptionsPanel(previewScheme);
    return myFontOptionsPanel;
  }

  @Override
  public boolean isModified() {
    boolean isUseEditorFont = AppConsoleFontOptions.getInstance().isUseEditorFont();
    return myFontOptionsPanel.myUseEditorFontBox.isSelected() ? !isUseEditorFont : isUseEditorFont || super.isModified();
  }

  @Override
  protected void applyFontPreferences() {
    boolean isUseEditorFont = myFontOptionsPanel.myUseEditorFontBox.isSelected();
    AppConsoleFontOptions.getInstance().setUseEditorFont(isUseEditorFont);
    if (!isUseEditorFont) {
      super.applyFontPreferences();
    }
  }

  @Override
  public void reset() {
    myFontOptionsPanel.myUseEditorFontBox.setSelected(AppConsoleFontOptions.getInstance().isUseEditorFont());
    super.reset();
  }

  private static class ConsoleFontOptionsPanel extends AppFontOptionsPanel {

    private JCheckBox myUseEditorFontBox;

    private ConsoleFontOptionsPanel(@NotNull EditorColorsScheme scheme) {
      super(scheme);
    }

    @NotNull
    @Override
    protected JComponent createControls() {
      JPanel outerPanel = new JPanel(new BorderLayout());
      myUseEditorFontBox = new JCheckBox(IdeBundle.message("configurable.console.font.use.editor.font"));
      myUseEditorFontBox.setSelected(isReadOnly());
      myUseEditorFontBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myUseEditorFontBox.isSelected()) {
            getEditorFontPreferences().copyTo(getFontPreferences());
          }
          updateOnChangedFont();
        }
      });
      myUseEditorFontBox.setBorder(JBUI.Borders.empty(10, 0));
      outerPanel.add(myUseEditorFontBox, BorderLayout.NORTH);
      JComponent innerControls = super.createControls();
      outerPanel.add(innerControls, BorderLayout.CENTER);
      return outerPanel;
    }


    @Override
    protected boolean isReadOnly() {
      return myUseEditorFontBox.isSelected();
    }

    private void updateOnEditorFontChange(@NotNull FontPreferences fontPreferences) {
      if (myUseEditorFontBox.isSelected()) {
        fontPreferences.copyTo(getFontPreferences());
        updateOnChangedFont();
      }
    }

    private @NotNull FontPreferences getEditorFontPreferences() {
      AppEditorFontConfigurable configurable = (AppEditorFontConfigurable)findConfigurable(this, AppEditorFontConfigurable.class);
      if (configurable != null) {
        AppEditorFontOptionsPanel panel = configurable.getPanel();
        if (panel != null) {
          return panel.getFontPreferences();
        }
      }
      return AppEditorFontOptions.getInstance().getFontPreferences();
    }
  }

  void updateOnEditorFontChange(@NotNull FontPreferences fontPreferences) {
    ObjectUtils.consumeIfNotNull(myFontOptionsPanel, panel->panel.updateOnEditorFontChange(fontPreferences));
  }

  public static class Provider extends ConfigurableProvider {

    @Override
    public @Nullable Configurable createConfigurable() {
      return new AppConsoleFontConfigurable();
    }

    @Override
    public boolean canCreateConfigurable() {
      return AppFontOptions.APP_CONSOLE_FONT_ENABLED;
    }
  }
}
