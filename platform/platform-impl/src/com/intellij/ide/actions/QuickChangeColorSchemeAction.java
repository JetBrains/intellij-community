// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    group.add(new DumbAwareAction(scheme.getDisplayName(), "", scheme == current ? AllIcons.Actions.Forward : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorsScheme(scheme);
        }
        EditorColorsScheme oldScheme = EditorColorsManager.getInstance().getGlobalScheme();
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
        changeLafIfNecessary(oldScheme, scheme);
      }
    });
  }

  public static void changeLafIfNecessary(@NotNull EditorColorsScheme oldScheme, EditorColorsScheme newScheme) {
    changeLafIfNecessary(oldScheme, newScheme, null);
  }

  public static void changeLafIfNecessary(@NotNull EditorColorsScheme oldScheme, EditorColorsScheme newScheme, @Nullable Runnable onDone) {
    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    final LafManager lafManager = LafManager.getInstance();
    boolean isDarkEditorTheme = ColorUtil.isDark(newScheme.getDefaultBackground());

    // 1. Before we start messing around with LaF changes, we better remember the OLD scheme for the current LaF,
    // because if the user decides to switch the theme, it will make no sense to remember the new scheme for the old theme (IDEA-323306).
    // 2. But we also need to prevent the LaF manager from remembering it automatically.
    // 3. In case the user does NOT decide to change the theme, we'll remember the new scheme later.
    lafManager.rememberSchemeForLaf(oldScheme);
    lafManager.setRememberSchemeForLaf(false);
    boolean lafChanged = false;

    UIManager.LookAndFeelInfo suitableLaf = null;
    String schemeName = Scheme.getBaseName(newScheme.getName());
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
        Messages.getYesButton(), Messages.getNoButton(),
        Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES) {

        lafManager.setCurrentLookAndFeel(suitableLaf != null ? suitableLaf : ((LafManagerImpl)lafManager).getDefaultDarkLaf(), true);
        lafChanged = true;
        lafManager.updateUI();
        SwingUtilities.invokeLater(DarculaInstaller::install);
      }
    } else if (!isDarkEditorTheme &&
               (StartupUiUtil.isUnderDarcula() || theme != null && theme.isDark())) {
      if (lafManager instanceof LafManagerImpl
          &&
          (/*applyAlways ||*/ Messages.showYesNoDialog(
            ApplicationBundle.message("color.scheme.theme.change.confirmation", "bright", productName),
            ApplicationBundle.message("color.scheme.theme.change.confirmation.title", productName),
            Messages.getYesButton(), Messages.getNoButton(),
            Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES)) {

        lafManager.setCurrentLookAndFeel(suitableLaf != null ? suitableLaf : ((LafManagerImpl)lafManager).getDefaultLightLaf(), true);
        lafChanged = true;
        lafManager.updateUI();
        SwingUtilities.invokeLater(DarculaInstaller::uninstall);
      }
    }

    lafManager.setRememberSchemeForLaf(true);
    if (!lafChanged) { // The user decided to keep the new scheme for the old theme, so remember it.
      lafManager.rememberSchemeForLaf(newScheme);
    }

    if (onDone != null) {
      SwingUtilities.invokeLater(onDone);
    }
  }

  @Override
  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1;
  }
}
