// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts;

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.AppFontOptions;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.Configurable.NoScroll;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class AppFontConfigurable implements SearchableConfigurable, Configurable.NoMargin, NoScroll {

  private final NotNullLazyValue<AppFontPanel> myFontPanelInstance =
    NotNullLazyValue.lazy(() -> new AppFontPanel(new AppFontPanel.FontOptionsPanelFactory() {
      @Override
      public @NotNull AppFontOptionsPanel create(@NotNull EditorColorsScheme previewScheme) {
        return createFontOptionsPanel(previewScheme);
      }
    }));

  @NotNull
  @Override
  public JComponent createComponent() {
    return getFontPanel().getComponent();
  }

  @Override
  public boolean isModified() {
    getFontPanel().updateWarning();
    return !getStoredPreferences().equals(getUIFontPreferences());
  }

  @Override
  public final void apply() {
    applyFontPreferences();
    EditorFontCache.getInstance().reset();
    ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
    EditorFactory.getInstance().refreshAllEditors();
  }

  protected void applyFontPreferences() {
    getFontOptions().update(getUIFontPreferences());
  }

  @NotNull
  private FontPreferences getUIFontPreferences() {
    return getFontPanel().getOptionsPanel().getFontPreferences();
  }

  @Override
  public void reset() {
    getStoredPreferences().copyTo(getUIFontPreferences());
    getFontPanel().getOptionsPanel().updateOnChangedFont();
  }

  @NotNull
  private FontPreferences getStoredPreferences() {
    return getFontOptions().getFontPreferences();
  }

  @NotNull
  protected abstract AppFontOptions<?> getFontOptions();

  @NotNull
  private AppFontPanel getFontPanel() {
    return myFontPanelInstance.getValue();
  }

  @Override
  public void disposeUIResources() {
    if (myFontPanelInstance.isComputed()) {
      Disposer.dispose(getFontPanel());
    }
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.colors";
  }

  @NotNull
  protected abstract AppFontOptionsPanel createFontOptionsPanel(@NotNull EditorColorsScheme previewScheme);

  protected static AppFontConfigurable findConfigurable(JComponent component, Class<? extends AppFontConfigurable> confClass) {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(component));
    return allSettings != null ? allSettings.find(confClass) : null;
  }
}
