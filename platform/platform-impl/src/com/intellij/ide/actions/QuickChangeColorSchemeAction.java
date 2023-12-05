// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoKt;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColorUtil;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public final class QuickChangeColorSchemeAction extends QuickSwitchSchemeAction {
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
    var name = scheme.getDisplayName();
    group.add(new DumbAwareAction(name, "", scheme == current ? AllIcons.Actions.Forward : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorScheme(scheme);
        }
        EditorColorsScheme oldScheme = EditorColorsManager.getInstance().getGlobalScheme();
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
        changeLafIfNecessary(oldScheme, scheme);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        if (UIThemeLookAndFeelInfoKt.isDefaultForTheme(scheme, LafManager.getInstance().getCurrentUIThemeLookAndFeel())) {
          e.getPresentation().putClientProperty(Presentation.PROP_VALUE, IdeBundle.message("scheme.theme.default"));
        }
      }
    });
  }

  public static void changeLafIfNecessary(@NotNull EditorColorsScheme oldScheme, EditorColorsScheme newScheme) {
    changeLafIfNecessary(oldScheme, newScheme, null);
  }

  public static void changeLafIfNecessary(@NotNull EditorColorsScheme oldScheme, EditorColorsScheme newScheme, @Nullable Runnable onDone) {
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    LafManager lafManager = LafManager.getInstance();
    boolean isDarkEditorTheme = ColorUtil.isDark(newScheme.getDefaultBackground());

    // 1. Before we start messing around with LaF changes, we better remember the OLD scheme for the current LaF,
    // because if the user decides to switch the theme, it will make no sense to remember the new scheme for the old theme (IDEA-323306).
    // 2. But we also need to prevent the LaF manager from remembering it automatically.
    // 3. In case the user does NOT decide to change the theme, we'll remember the new scheme later.
    lafManager.rememberSchemeForLaf(oldScheme);
    lafManager.setRememberSchemeForLaf(false);
    boolean lafChanged = false;

    UIThemeLookAndFeelInfo suitableLaf = null;
    String schemeName = Scheme.getBaseName(newScheme.getName());
    for (UIThemeLookAndFeelInfo laf : SequencesKt.asIterable(UiThemeProviderListManager.Companion.getInstance().getLaFs())) {
      if (schemeName.equals(laf.getEditorSchemeId())) {
        suitableLaf = laf;
        break;
      }
    }

    UIThemeLookAndFeelInfo currentLafInfo = lafManager.getCurrentUIThemeLookAndFeel();
    Boolean isDark = currentLafInfo == null ? null : currentLafInfo.isDark();

    if (isDarkEditorTheme && (isDark != null && !isDark)) {
      if (/*applyAlways ||*/ Messages.showYesNoDialog(
        ApplicationBundle.message("color.scheme.theme.change.confirmation", "dark", productName),
        ApplicationBundle.message("color.scheme.theme.change.confirmation.title", productName),
        Messages.getYesButton(), Messages.getNoButton(),
        Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES) {

        lafManager.setCurrentLookAndFeel(suitableLaf == null ? Objects.requireNonNull(lafManager.getDefaultDarkLaf()) : suitableLaf, true);
        lafChanged = true;
        lafManager.updateUI();
        SwingUtilities.invokeLater(DarculaInstaller::install);
      }
    }
    else if (!isDarkEditorTheme && (isDark != null && isDark)) {
      if (/*applyAlways ||*/Messages.showYesNoDialog(
            ApplicationBundle.message("color.scheme.theme.change.confirmation", "bright", productName),
            ApplicationBundle.message("color.scheme.theme.change.confirmation.title", productName),
            Messages.getYesButton(), Messages.getNoButton(),
            Messages.getQuestionIcon()/*, doNotAskOption*/) == Messages.YES) {

        lafManager.setCurrentLookAndFeel(suitableLaf == null ? Objects.requireNonNull(lafManager.getDefaultLightLaf()) : suitableLaf, true);
        lafChanged = true;
        lafManager.updateUI();
        SwingUtilities.invokeLater(DarculaInstaller::uninstall);
      }
    }

    lafManager.setRememberSchemeForLaf(true);
    if (!lafChanged) {
      // the user decided to keep the new scheme for the old theme, so remember it.
      lafManager.rememberSchemeForLaf(newScheme);
    }

    if (onDone != null) {
      SwingUtilities.invokeLater(onDone);
    }
  }

  @Override
  protected JBPopupFactory.ActionSelectionAid getAidMethod() {
    return JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING;
  }

  @Override
  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1;
  }
}
