// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsPresentation;
import com.intellij.ui.tabs.impl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl implements JBEditorTabsBase {
  /**
   * @deprecated use {@link #myTabPainter}.
   */
  @Deprecated
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);

  private boolean myAlphabeticalModeChanged = false;

  public JBEditorTabs(@Nullable Project project, @Nullable IdeFocusManager focusManager, @NotNull Disposable parentDisposable) {
    super(project, focusManager, parentDisposable);

    setSupportsCompression(true);
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    resetTabsCache();
    relayout(true, false);

    super.uiSettingsChanged(uiSettings);
  }

  /**
   * @deprecated Use {@link #JBEditorTabs(Project, IdeFocusManager, Disposable)}
   */
  @Deprecated
  public JBEditorTabs(@Nullable Project project,
                      @SuppressWarnings("unused") @NotNull ActionManager actionManager,
                      @Nullable IdeFocusManager focusManager,
                      @NotNull Disposable parent) {
    this(project, focusManager, parent);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeeded() && supportsCompression()) {
      return new CompressibleSingleRowLayout(this);
    }
    else {
      return new ScrollableSingleRowLayout(this);
    }
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  public boolean useSmallLabels() {
    return UISettings.getInstance().getUseSmallLabelsOnTabs();
  }

  @Override
  public boolean isAlphabeticalMode() {
    if (myAlphabeticalModeChanged) {
      return super.isAlphabeticalMode();
    }
    return UISettings.getInstance().getSortTabsAlphabetically();
  }

  @Override
  public JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode) {
    myAlphabeticalModeChanged = true;
    return super.setAlphabeticalMode(alphabeticalMode);
  }

  @Override
  public void setEmptySpaceColorCallback(@NotNull Supplier<? extends Color> callback) {
  }

  /**
   * @deprecated You should move the painting logic to an implementation of {@link JBTabPainter} interface
   */
  @Deprecated
  protected Color getEmptySpaceColor() {
    return myTabPainter.getBackgroundColor();
  }
}
