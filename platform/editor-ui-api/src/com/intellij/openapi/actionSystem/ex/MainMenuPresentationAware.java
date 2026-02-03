// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

/**
 * A marker interface for some main menu action to always show icons
 *
 * @author Konstantin Bulenkov
 * @deprecated use {@link ActionUtil#SHOW_ICON_IN_MAIN_MENU} instead
 */
@Deprecated(forRemoval = true)
public interface MainMenuPresentationAware {
  boolean alwaysShowIconInMainMenu();
}
