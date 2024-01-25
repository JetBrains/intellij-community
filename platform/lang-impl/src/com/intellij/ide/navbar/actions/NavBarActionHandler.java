// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.actions;

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public interface NavBarActionHandler {

  DataKey<NavBarActionHandler> NAV_BAR_ACTION_HANDLER = DataKey.create("nav.bar.action.handler");

  boolean isNodePopupSpeedSearchActive();

  void moveHome();

  void moveLeft();

  void moveRight();

  void moveEnd();

  void moveUpDown();

  void enter();
}
