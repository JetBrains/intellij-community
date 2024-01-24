// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.actions.NavBarActionHandler;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.ide.navbar.actions.NavBarActionHandler.NAV_BAR_ACTION_HANDLER;

public sealed abstract class NavBarActions extends AnAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {
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

  private static @Nullable NavBarActionHandler actionHandler(@NotNull AnActionEvent event) {
    return event.getData(NAV_BAR_ACTION_HANDLER);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    actionPerformed(Objects.requireNonNull(actionHandler(event)));
  }

  protected boolean isEnabledWithActivePopupSpeedSearch() {
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

  public static final class Enter extends NavBarActions {
    @Override
    void actionPerformed(@NotNull NavBarActionHandler handler) {
      handler.enter();
    }
  }
}