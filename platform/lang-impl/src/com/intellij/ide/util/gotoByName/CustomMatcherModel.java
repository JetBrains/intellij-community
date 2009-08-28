package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 * @date Mar 11, 2009
 */
public interface CustomMatcherModel {
  /**
   * Allows to implement custom matcher for matching itemps from ChooseByName popup
   * with user pattern
   * @param popupItem Item from list
   * @param userPattern Pattern defined by user in Choose by name popup
   * @return True if matches
   */
  boolean matches(@NotNull final String popupItem, @NotNull final String userPattern);
}
