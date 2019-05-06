// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorsScheme(scheme);
        }
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
        changeLafIfNecessary(scheme);
      }
    });
  }

  public static void changeLafIfNecessary(EditorColorsScheme scheme) {
    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    final LafManager lafManager = LafManager.getInstance();
    boolean isDarkEditorTheme = ColorUtil.isDark(scheme.getDefaultBackground());

    UIManager.LookAndFeelInfo suitableLaf = null;
    String schemeName = SchemeManager.getDisplayName(scheme);
    for (UIManager.LookAndFeelInfo laf : lafManager.getInstalledLookAndFeels()) {
      if (laf instanceof UIThemeBasedLookAndFeelInfo &&
               schemeName.equals(((UIThemeBasedLookAndFeelInfo)laf).getTheme().getEditorSchemeName())) {
        suitableLaf = laf;
        break;
      }
    }

    UIManager.LookAndFeelInfo currentLafInfo = lafManager.getCurrentLookAndFeel();
    UITheme theme = currentLafInfo instanceof UIThemeBasedLookAndFeelInfo ?
                      ((UIThemeBasedLookAndFeelInfo)currentLafInfo).getTheme() : null;

    if (isDarkEditorTheme &&
        (UIUtil.isUnderIntelliJLaF() || theme != null && !theme.isDark())) {
      if (/*applyAlways ||*/ Messages.showYesNoDialog(
          ApplicationBundle.message("color.scheme.theme.change.confirmation", "dark", productName),
          ApplicationBundle.message("color.scheme.theme.change.confirmation.title", productName),
          Messages.YES_BUTTON, Messages.NO_BUTTON,
          Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES) {

        lafManager.setCurrentLookAndFeel(suitableLaf != null ? suitableLaf : new DarculaLookAndFeelInfo());
        lafManager.updateUI();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(DarculaInstaller::install);
      }
    } else if (!isDarkEditorTheme &&
               (UIUtil.isUnderDarcula() || theme != null && theme.isDark())) {
      if (lafManager instanceof LafManagerImpl
          &&
          (/*applyAlways ||*/ Messages.showYesNoDialog(
            ApplicationBundle.message("color.scheme.theme.change.confirmation", "bright", productName),
            ApplicationBundle.message("color.scheme.theme.change.confirmation.title", productName),
            Messages.YES_BUTTON, Messages.NO_BUTTON,
            Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES)) {

        lafManager.setCurrentLookAndFeel(suitableLaf != null ? suitableLaf : ((LafManagerImpl)lafManager).getDefaultLaf());
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
