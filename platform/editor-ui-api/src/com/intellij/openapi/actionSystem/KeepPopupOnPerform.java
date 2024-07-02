// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ui.UISettings;

/**
 * Enum for the {@link Presentation#getKeepPopupOnPerform()} property.
 */
public enum KeepPopupOnPerform {
  /**
   * Never keep a popup open.
   * That is the default value for all non-toggles.
   * <p>
   * Use it explicitly for slow and heavy toggles or toggles that open windows or destroy menus.
   */
  Never,

  /**
   * Keep a popup open only if the user explicitly requests that via a keyboard modifier.
   * That is for actions in a popups we want to close quickly on a single click
   * even if the UI is configured to keep popups for toggles on a single click.
   * <p>
   * Use it explicitly for some toggles to restore the old "close popup as usual" behavior
   * while respecting the explicit user choice.
   *
   * @see UISettings#getKeepPopupsForToggles()
   */
  IfRequested,

  /**
   * Keep a popup if the user explicitly requests that via a keyboard modifier,
   * or the UI is configured to keep popups for toggles on a single click.
   * That is the default value for all toggles.
   * <p>
   * Use it explicitly for non-toggles if you want the toggle-like behavior.
   *
   * @see com.intellij.openapi.actionSystem.ToggleAction
   */
  IfPreferred,

  /**
   * Keep a popup when an action is performed in all circumstances.
   * That is for toggles that modify their popups in some way.
   */
  Always
}
