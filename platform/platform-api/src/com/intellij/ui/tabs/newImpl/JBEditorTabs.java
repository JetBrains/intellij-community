// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsPresentation;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.newImpl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl implements JBEditorTabsBase {
  public static final String TABS_ALPHABETICAL_KEY = "tabs.alphabetical";
  private final Map<TabInfo, Disposable> myTabDisposables = new HashMap<>();

  /**
   * @Deprecated use {@link #myTabPainter}.
   */
  @Deprecated
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);
  private boolean myAlphabeticalModeChanged = false;

  public JBEditorTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
    Registry.get(TABS_ALPHABETICAL_KEY).addListener(new RegistryValueListener.Adapter() {

      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        ApplicationManager.getApplication().invokeLater(() -> {
          resetTabsCache();
          relayout(true, false);
        });
      }
    }, parent);
    setSupportsCompression(true);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeed() && supportsCompression()) {
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
  @NotNull
  public TabInfo addTabSilently(@NotNull TabInfo info, int index, @NotNull Disposable tabDisposable) {
    TabInfo tab = super.addTabSilently(info, index);
    Disposer.register(this, tabDisposable);
    myTabDisposables.put(tab, tabDisposable);
    return tab;
  }

  @Override
  @NotNull
  public ActionCallback removeTab(@NotNull TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus) {
    Disposable tabDisposable = myTabDisposables.remove(info);
    if (tabDisposable != null) {
      Disposer.dispose(tabDisposable);
    }
    return super.removeTab(info, forcedSelectionTransfer, transferFocus);
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
    return Registry.is(TABS_ALPHABETICAL_KEY);
  }

  @Override
  public JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode) {
    myAlphabeticalModeChanged = true;
    return super.setAlphabeticalMode(alphabeticalMode);
  }

  public static void setEditorTabsAlphabeticalMode(boolean on) {
    Registry.get(TABS_ALPHABETICAL_KEY).setValue(on);
  }

  @Override
  public void setEmptySpaceColorCallback(@NotNull Supplier<Color> callback) {
  }

  /**
   * @deprecated You should move the painting logic to an implementation of {@link JBTabPainter} interface
   */
  @Deprecated
  protected Color getEmptySpaceColor() {
    return myTabPainter.getBackgroundColor();
  }
}
