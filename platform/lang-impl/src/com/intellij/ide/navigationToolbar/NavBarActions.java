// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.actions.NavBarActionHandler;
import com.intellij.ide.navbar.ide.NavBarIdeUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.ide.navbar.actions.NavBarActionHandler.NAV_BAR_ACTION_HANDLER;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

public sealed abstract class NavBarActions extends AnAction implements DumbAware {
  NavBarActions() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    NavBarActionHandler handler = actionHandler(event);
    boolean isEnabled = handler != null && (isEnabledWithActivePopupSpeedSearch() || !handler.isNodePopupSpeedSearchActive());
    event.getPresentation().setEnabled(isEnabled);
  }

  private @Nullable NavBarActionHandler actionHandler(@NotNull AnActionEvent event) {
    return NavBarIdeUtil.isNavbarV2Enabled()
           ? isEnabledInV2() ? event.getData(NAV_BAR_ACTION_HANDLER) : null
           : ComponentUtil.getParentOfType(NavBarPanel.class, event.getData(CONTEXT_COMPONENT));
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    actionPerformed(Objects.requireNonNull(actionHandler(event)));
  }

  protected boolean isEnabledWithActivePopupSpeedSearch() {
    return true;
  }

  protected boolean isEnabledInV2() {
    return true;
  }

  abstract void actionPerformed(@NotNull NavBarActionHandler handler);

  public static final class Home extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveHome();
    }
  }

  public static final class End extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveEnd();
    }
  }

  public static final class Up extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveUpDown();
    }
  }

  public static final class Down extends NavBarActions {
    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveUpDown();
    }
  }

  public static final class Left extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveLeft();
    }
  }

  public static final class Right extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.moveRight();
    }
  }

  /**
   * @deprecated unused in ide.navBar.v2
   */
  @Deprecated
  public static final class Escape extends NavBarActions {

    @Override
    protected boolean isEnabledInV2() {
      return false;
    }

    @Override
    protected boolean isEnabledWithActivePopupSpeedSearch() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.escape();
    }
  }

  public static final class Enter extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.enter();
    }
  }

  /**
   * @deprecated unused in ide.navBar.v2
   */
  @Deprecated
  public static final class Navigate extends NavBarActions {

    @Override
    protected boolean isEnabledInV2() {
      return false;
    }

    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.navigate();
    }
  }
}