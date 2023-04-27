// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabsPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JBEditorTabs extends JBTabsImpl implements JBEditorTabsBase {
  public static final Key<Boolean> MARK_MODIFIED_KEY = Key.create("EDITOR_TABS_MARK_MODIFIED");
  /**
   * @deprecated use {@link #myTabPainter}.
   */
  @Deprecated(forRemoval = true)
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);

  private boolean myAlphabeticalModeChanged = false;

  public JBEditorTabs(@Nullable Project project, @SuppressWarnings("unused") @Nullable IdeFocusManager focusManager, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
    setSupportsCompression(true);
  }

  public JBEditorTabs(@Nullable Project project, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
    setSupportsCompression(true);
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    resetTabsCache();
    relayout(true, false);

    super.uiSettingsChanged(uiSettings);
  }

  /**
   * @deprecated Use {@link #JBEditorTabs(Project, Disposable)}
   */
  @Deprecated
  public JBEditorTabs(@Nullable Project project,
                      @SuppressWarnings("unused") @NotNull ActionManager actionManager,
                      @Nullable IdeFocusManager focusManager,
                      @NotNull Disposable parent) {
    this(project, parent);
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  public boolean useSmallLabels() {
    return UISettings.getInstance().getUseSmallLabelsOnTabs() && !ExperimentalUI.isNewUI();
  }

  @Override
  public boolean isAlphabeticalMode() {
    if (myAlphabeticalModeChanged) {
      return super.isAlphabeticalMode();
    }
    return UISettings.getInstance().getSortTabsAlphabetically();
  }

  @Override
  public @NotNull JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode) {
    myAlphabeticalModeChanged = true;
    return super.setAlphabeticalMode(alphabeticalMode);
  }

  public boolean shouldPaintBottomBorder() {
    return true;
  }
}
