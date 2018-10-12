// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.newui.VerticalLayout;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.ide.actions.QuickChangeLookAndFeel.switchLafAndUpdateUI;

public final class AccessibilityConfigurable implements SearchableConfigurable {
  public static final String ID = "preferences.accessibility";
  private static final Logger LOG = Logger.getInstance(AccessibilityConfigurable.class);
  private final AtomicReference<View> viewReference = new AtomicReference<>();

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getDisplayName() {
    return IdeBundle.message("title.accessibility");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    View view = viewReference.get();
    return view == null ? null : view.cbHighContrast;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    View view = new View();
    View old = viewReference.getAndSet(view);
    LOG.assertTrue(old == null, "request to create component that is already created");
    return view.pMain;
  }

  @Override
  public boolean isModified() {
    View view = viewReference.get();
    return view != null && view.isModified();
  }

  @Override
  public void reset() {
    View view = viewReference.get();
    if (view != null) view.reset();
  }

  @Override
  public void apply() {
    View view = viewReference.get();
    if (view != null) view.apply();
  }

  private static boolean isDarcula(LookAndFeelInfo info) {
    return info instanceof DarculaLookAndFeelInfo;
  }

  private static boolean isDefault(LookAndFeelInfo info) {
    return !isDarcula(info) && !isHighContrast(info);
  }

  private static boolean isHighContrast(LookAndFeelInfo info) {
    return isHighContrastLight(info) || isHighContrastDark(info);
  }

  private static boolean isHighContrastLight(@SuppressWarnings("unused") LookAndFeelInfo info) {
    return false; // TODO: support light theme in future releases
  }

  private static boolean isHighContrastDark(LookAndFeelInfo info) {
    if (info instanceof UIThemeBasedLookAndFeelInfo) {
      UITheme theme = ((UIThemeBasedLookAndFeelInfo)info).getTheme();
      if ("JetBrainsHighContrastTheme".equals(theme.getId())) return true;
    }
    return false;
  }

  @Nullable
  private static LookAndFeelInfo findLookAndFeel(@NotNull Predicate<LookAndFeelInfo> predicate) {
    for (LookAndFeelInfo info : LafManager.getInstance().getInstalledLookAndFeels()) {
      if (predicate.test(info)) return info;
    }
    return null;
  }

  @NotNull
  @SuppressWarnings("UseOfObsoleteCollectionType")
  static Vector<LookAndFeelInfo> getInstalledLookAndFeels(boolean withHighContrast) {
    Vector<LookAndFeelInfo> vector = new Vector<>();
    for (LookAndFeelInfo info : LafManager.getInstance().getInstalledLookAndFeels()) {
      if (withHighContrast || !isHighContrast(info)) vector.add(info);
    }
    return vector;
  }

  private static final class View {
    private final JPanel pMain = new JPanel(new VerticalLayout(JBUI.scale(5)));
    private final JCheckBox cbHighContrast = new JCheckBox(IdeBundle.message("checkbox.support.high.contrast"));
    private final JCheckBox cbScreenReaders = new JCheckBox(IdeBundle.message("checkbox.support.screen.readers"));
    private final ColorBlindnessPanel pColorBlindness = new ColorBlindnessPanel();

    View() {
      pMain.add(cbHighContrast);
      pMain.add(cbScreenReaders);
      pMain.add(pColorBlindness);
    }

    boolean isModified() {
      UISettings ui = UISettings.getInstance();
      if (cbHighContrast.isEnabled() && cbHighContrast.isSelected() != ui.getHighContrast()) return true;

      ColorBlindness blindness = pColorBlindness.getColorBlindness();
      if (blindness != ui.getColorBlindness()) return true;

      GeneralSettings general = GeneralSettings.getInstance();
      if (cbScreenReaders.isEnabled() && cbScreenReaders.isSelected() != general.isSupportScreenReaders()) return true;

      return false;
    }

    void reset() {
      UISettings ui = UISettings.getInstance();
      cbHighContrast.setEnabled(findLookAndFeel(AccessibilityConfigurable::isHighContrast) != null);
      cbHighContrast.setSelected(ui.getHighContrast());
      pColorBlindness.setColorBlindness(ui.getColorBlindness());

      GeneralSettings general = GeneralSettings.getInstance();
      cbScreenReaders.setSelected(general.isSupportScreenReaders());
      if (GeneralSettings.isSupportScreenReadersOverridden()) {
        cbScreenReaders.setEnabled(false);
        cbScreenReaders.setToolTipText("The option is overridden by the JVM property: \"" + GeneralSettings.SUPPORT_SCREEN_READERS + "\"");
      }
    }

    void apply() {
      UISettings ui = UISettings.getInstance();
      boolean updateHighContrast = cbHighContrast.isEnabled() && cbHighContrast.isSelected() != ui.getHighContrast();
      if (updateHighContrast) ui.setHighContrast(cbHighContrast.isSelected());

      ColorBlindness blindness = pColorBlindness.getColorBlindness();
      boolean updateColorBlindness = blindness != ui.getColorBlindness();
      if (updateColorBlindness) ui.setColorBlindness(blindness);

      GeneralSettings general = GeneralSettings.getInstance();
      boolean updateScreenReaders = cbScreenReaders.isEnabled() && cbScreenReaders.isSelected() != general.isSupportScreenReaders();
      if (updateScreenReaders) general.setSupportScreenReaders(cbScreenReaders.isSelected());

      if (updateHighContrast) {
        LookAndFeelInfo replacement = null;
        LookAndFeelInfo current = LafManager.getInstance().getCurrentLookAndFeel();
        if (cbHighContrast.isSelected()) {
          if (!isHighContrast(current)) {
            if (isDarcula(current)) {
              // replace the Darcula L&F with dark high contrast theme
              replacement = findLookAndFeel(AccessibilityConfigurable::isHighContrastDark);
            }
            else {
              // replace another L&F with light high contrast theme
              replacement = findLookAndFeel(AccessibilityConfigurable::isHighContrastLight);
            }
            if (replacement == null) {
              // try to find any high contrast theme
              replacement = findLookAndFeel(AccessibilityConfigurable::isHighContrast);
            }
          }
        }
        else if (isHighContrastLight(current)) {
          // replace light high contrast theme with the Default L&F
          replacement = findLookAndFeel(AccessibilityConfigurable::isDefault);
        }
        else if (isHighContrastDark(current)) {
          // replace dark high contrast theme with Darcula L&F
          replacement = findLookAndFeel(AccessibilityConfigurable::isDarcula);
        }
        if (replacement != null) {
          // replace current L&F with the found one
          switchLafAndUpdateUI(LafManager.getInstance(), replacement, true);
        }
      }
      if (updateHighContrast || updateColorBlindness) {
        ui.fireUISettingsChanged();
      }
      if (updateColorBlindness) {
        ServiceKt.getStateStore(ApplicationManager.getApplication()).reloadState(DefaultColorSchemesManager.class);
        ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
      }
    }
  }
}
