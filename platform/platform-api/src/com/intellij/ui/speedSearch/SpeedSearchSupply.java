// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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


  @Nullable
  public static SpeedSearchSupply getSupply(@NotNull final JComponent component) {
    return getSupply(component, false);
  }

  @Nullable
  public static SpeedSearchSupply getSupply(@NotNull final JComponent component, boolean evenIfInactive) {
    SpeedSearchSupply speedSearch = (SpeedSearchSupply)component.getClientProperty(SPEED_SEARCH_COMPONENT_MARKER);

    if (evenIfInactive) {
      return speedSearch;
    }

    return speedSearch != null && speedSearch.isPopupActive() ? speedSearch : null;
  }

  @Nullable
  public abstract Iterable<TextRange> matchingFragments(@NotNull final String text);

  /**
   * Selects element according to search criteria changes
   */
  public abstract void refreshSelection();

  public abstract boolean isPopupActive();

  @Nullable
  public String getEnteredPrefix() {
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
}
