// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.im.InputMethodRequests;
import java.beans.PropertyChangeListener;

import static com.intellij.ui.JBColor.namedColor;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SpeedSearchSupply {
  /** @deprecated Use {@code SpeedSearchSupply.getSupply} */
  @Deprecated(forRemoval = true)
  public static final String SEARCH_QUERY_KEY = "SEARCH_QUERY";

  private static final Key<SpeedSearchSupply> SPEED_SEARCH_COMPONENT_MARKER = Key.create("SPEED_SEARCH_COMPONENT_MARKER");

  /** @deprecated Use {@link PlatformDataKeys#SPEED_SEARCH_TEXT} instead */
  @Deprecated(forRemoval = true)
  public static final DataKey<String> SPEED_SEARCH_CURRENT_QUERY = PlatformDataKeys.SPEED_SEARCH_TEXT;

  public static final String ENTERED_PREFIX_PROPERTY_NAME = "enteredPrefix";

  protected static final JBColor BACKGROUND_COLOR = namedColor("SpeedSearch.background", namedColor("Editor.SearchField.background", UIUtil.getTextFieldBackground()));
  protected static final JBColor BORDER_COLOR = namedColor("SpeedSearch.borderColor", namedColor("Editor.Toolbar.borderColor", JBColor.LIGHT_GRAY));
  protected static final JBColor FOREGROUND_COLOR = namedColor("SpeedSearch.foreground", namedColor("TextField.foreground", UIUtil.getToolTipForeground()));
  protected static final JBColor ERROR_FOREGROUND_COLOR = namedColor("SpeedSearch.errorForeground", namedColor("SearchField.errorForeground", JBColor.RED));


  public static @Nullable SpeedSearchSupply getSupply(final @NotNull JComponent component) {
    return getSupply(component, false);
  }

  public static @Nullable SpeedSearchSupply getSupply(final @NotNull JComponent component, boolean evenIfInactive) {
    SpeedSearchSupply speedSearch = (SpeedSearchSupply)component.getClientProperty(SPEED_SEARCH_COMPONENT_MARKER);

    if (evenIfInactive) {
      return speedSearch;
    }

    return speedSearch != null && speedSearch.isPopupActive() ? speedSearch : null;
  }

  /**
   * Checks if this implementation of speed search has its own navigation actions.
   * <p>
   *   Some implementations have their own actions for up/down, to go to the next/previous
   *   match. This method is used to determine if it's the case.
   * </p>
   * @return true iff speed search has its own action for navigating the contents of the component
   */
  public boolean supportsNavigation() {
    return false;
  }

  public abstract @Nullable Iterable<TextRange> matchingFragments(final @NotNull String text);

  /**
   * Selects element according to search criteria changes
   */
  public abstract void refreshSelection();

  public abstract boolean isPopupActive();

  public @Nullable String getEnteredPrefix() {
    return null;
  }

  protected void installSupplyTo(@NotNull JComponent component) {
    installSupplyTo(component, true);
  }

  public void installSupplyTo(@NotNull JComponent component, boolean withRepaint) {
    component.putClientProperty(SPEED_SEARCH_COMPONENT_MARKER, this);
    if(withRepaint) addChangeListener(evt -> component.repaint());
  }

  public abstract void addChangeListener(@NotNull PropertyChangeListener listener);
  public abstract void removeChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * Find an element matching the searching query in the underlying component and select it there. Speed-search popup is not affected.
   * @param searchQuery text that the selected element should match
   */
  public abstract void findAndSelectElement(@NotNull String searchQuery);

  public boolean isObjectFilteredOut(Object o) {
    return false;
  }

  public InputMethodRequests getInputMethodRequests() {
    return null;
  }

  @ApiStatus.Experimental
  @FunctionalInterface
  public interface SpeedSearchLocator {
    /**
     * Returns location and size of SpeedSearch popup invoked on the {@code target}
     *
     * @param target a component for speed search
     * @return location and size
     */
    @Nullable
    RelativeRectangle getSizeAndLocation(JComponent target);
  }
}
