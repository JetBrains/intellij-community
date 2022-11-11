// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

public abstract class NavBarActions extends AnAction implements DumbAware {
  NavBarActions() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void update(@NotNull AnActionEvent event) {
    NavBarPanel panel = ComponentUtil.getParentOfType(NavBarPanel.class, event.getData(CONTEXT_COMPONENT));
    boolean isEnabled = panel != null
                        && (isEnabledWithActivePopupSpeedSearch() || !panel.isNodePopupSpeedSearchActive());
    event.getPresentation().setEnabled(isEnabled);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    NavBarPanel panel = ComponentUtil.getParentOfType(NavBarPanel.class, event.getData(CONTEXT_COMPONENT));
    if (panel != null) actionPerformed(panel);
  }

  protected boolean isEnabledWithActivePopupSpeedSearch() {
    return true;
  }

  abstract void actionPerformed(@NotNull NavBarPanel panel);

  public static final class Home extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveHome();
    }
  }

  public static final class End extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveEnd();
    }
  }

  public static final class Up extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveDown();
    }
  }

  public static final class Down extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveDown();
    }
  }

  public static final class Left extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveLeft();
    }
  }

  public static final class Right extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.moveRight();
    }
  }

  public static final class Escape extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.escape();
    }
  }

  public static final class Enter extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.enter();
    }
  }

  public static final class Navigate extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarPanel panel) {
      panel.navigate();
    }
  }
}