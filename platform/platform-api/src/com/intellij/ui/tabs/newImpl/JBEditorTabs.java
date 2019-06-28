// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsPresentation;
import com.intellij.ui.tabs.newImpl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowLayout;
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

  public JBEditorTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
    ApplicationManager.getApplication().getMessageBus().connect(parent).subscribe(UISettingsListener.TOPIC, (settings) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        resetTabsCache();
        relayout(true, false);
      });
    });
    setSupportsCompression(true);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeeded() && supportsCompression()) {
      return new CompressibleSingleRowLayout(this);
    }
    else if (ApplicationManager.getApplication().isInternal() || Registry.is("editor.use.scrollable.tabs")) {
      return new ScrollableSingleRowLayout(this);
    }
    return super.createSingleRowLayout();
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  public boolean isGhostsAlwaysVisible() {
    return false;
  }

  @Override
  public boolean useSmallLabels() {
    return UISettings.getInstance().getUseSmallLabelsOnTabs();
  }

  @Override
  public boolean useBoldLabels() {
    return SystemInfo.isMac && Registry.is("ide.mac.boldEditorTabs");
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
