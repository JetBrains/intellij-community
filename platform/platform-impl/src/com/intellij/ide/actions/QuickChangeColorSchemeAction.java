// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class QuickChangeColorSchemeAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    EditorColorsScheme current = EditorColorsManager.getInstance().getGlobalScheme();
    for (EditorColorsScheme scheme : EditorColorsManager.getInstance().getAllSchemes()) {
      addScheme(group, current, scheme, false);
    }
  }

  private static void addScheme(final DefaultActionGroup group,
                                final EditorColorsScheme current,
                                final EditorColorsScheme scheme,
                                final boolean addScheme) {
    group.add(new DumbAwareAction(SchemeManager.getDisplayName(scheme), "", scheme == current ? ourCurrentAction : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorsScheme(scheme);
        }
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
        changeLafIfNecessary(ColorUtil.isDark(scheme.getDefaultBackground()));
      }
    });
  }

  public static void changeLafIfNecessary(boolean isDarkEditorTheme) {
    String propKey = "change.laf.on.editor.theme.change";
    String value = PropertiesComponent.getInstance().getValue(propKey);
    if ("false".equals(value)) return;
    boolean applyAlways = "true".equals(value);
    DialogWrapper.DoNotAskOption doNotAskOption = new DialogWrapper.DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected) {
          PropertiesComponent.getInstance().setValue(propKey, Boolean.toString(exitCode == Messages.YES));
        }
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }
    };

    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    final LafManager lafManager = LafManager.getInstance();
    if (isDarkEditorTheme && !UIUtil.isUnderDarcula()) {
      if (applyAlways || Messages.showYesNoDialog(
        "Looks like you have set a dark editor theme. Would you like to set dark theme for entire " + productName,
        "Change " + productName + " theme", Messages.YES_BUTTON, Messages.NO_BUTTON,
        Messages.getQuestionIcon(), doNotAskOption) == Messages.YES) {
        lafManager.setCurrentLookAndFeel(new DarculaLookAndFeelInfo());
        lafManager.updateUI();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(DarculaInstaller::install);
      }
    } else if (!isDarkEditorTheme && UIUtil.isUnderDarcula()) {

      if (lafManager instanceof LafManagerImpl
          &&
          (applyAlways || Messages.showYesNoDialog(
            "Looks like you have set a bright editor theme. Would you like to set bright theme for entire " + productName,
            "Change " + productName + " theme", Messages.YES_BUTTON, Messages.NO_BUTTON,
            Messages.getQuestionIcon(), doNotAskOption) == Messages.YES)) {
        lafManager.setCurrentLookAndFeel(((LafManagerImpl)lafManager).getDefaultLaf());
        lafManager.updateUI();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(DarculaInstaller::uninstall);
      }
    }
  }

  @Override
  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1;
  }
}
