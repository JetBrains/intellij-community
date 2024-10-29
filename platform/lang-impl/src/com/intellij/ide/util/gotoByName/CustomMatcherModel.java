// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public interface CustomMatcherModel {
  /**
   * Allows to implement custom matcher for matching items from ChooseByName popup
   * with user pattern
   * @param popupItem Item from list
   * @param userPattern Pattern defined by user in Choose by name popup
   * @return True if matches
   */
  boolean matches(final @NotNull String popupItem, final @NotNull String userPattern);
}
