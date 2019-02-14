// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.newImpl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl {
  public static final String TABS_ALPHABETICAL_KEY = "tabs.alphabetical";

  /**
   * @Deprecated use {@link #tabPainter}.
   */
  @Deprecated
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);


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
  public boolean supportsCompression() {
    return true;
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
    return Registry.is(TABS_ALPHABETICAL_KEY);
  }

  public static void setAlphabeticalMode(boolean on) {
    Registry.get(TABS_ALPHABETICAL_KEY).setValue(on);
  }

  /**
   * @deprecated You should move the painting logic to an implementation of {@link JBTabPainter} interface
   */
  @Deprecated
  protected Color getEmptySpaceColor() {
    return tabPainter.getBackgroundColor();
  }
}
