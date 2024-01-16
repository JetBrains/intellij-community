// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.DynamicBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.util.BitUtil;
import com.intellij.util.SmartFMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * The presentation of an action in a specific place in the user interface.
 *
 * @see AnAction
 * @see ActionPlaces
 */
public final class Presentation implements Cloneable {
  private static final Logger LOG = Logger.getInstance(Presentation.class);

  public static final Supplier<String> NULL_STRING = () -> null;

  // Property keys for the PropertyChangeListener API
  public static final @NonNls String PROP_TEXT = "text";
  public static final @NonNls String PROP_TEXT_WITH_SUFFIX = "textWithSuffix";
  public static final @NonNls String PROP_MNEMONIC_KEY = "mnemonicKey";
  public static final @NonNls String PROP_MNEMONIC_INDEX = "mnemonicIndex";
  public static final @NonNls String PROP_DESCRIPTION = "description";
  public static final @NonNls String PROP_ICON = "icon";
  public static final @NonNls String PROP_DISABLED_ICON = "disabledIcon";
  public static final @NonNls String PROP_SELECTED_ICON = "selectedIcon";
  public static final @NonNls String PROP_HOVERED_ICON = "hoveredIcon";
  public static final @NonNls String PROP_VISIBLE = "visible";
  public static final @NonNls String PROP_ENABLED = "enabled";
  // Do not add Key constants here, especially with PROP_ prefix. Find a better place.

  /** Use {@link com.intellij.openapi.actionSystem.ex.ActionUtil#SECONDARY_TEXT} instead */
  @Deprecated(forRemoval = true)
  public static final @NonNls Key<@Nls String> PROP_VALUE = Key.create("SECONDARY_TEXT");

  public static final double DEFAULT_WEIGHT = 0;
  public static final double HIGHER_WEIGHT = 42;
  public static final double EVEN_HIGHER_WEIGHT = 239;

  private static final int IS_ENABLED = 0x1;
  private static final int IS_VISIBLE = 0x2;
  private static final int IS_MULTI_CHOICE = 0x4;
  private static final int IS_POPUP_GROUP = 0x10;
  private static final int IS_PERFORM_GROUP = 0x20;
  private static final int IS_HIDE_GROUP_IF_EMPTY = 0x40;
  private static final int IS_DISABLE_GROUP_IF_EMPTY = 0x80;
  private static final int IS_TEMPLATE = 0x1000;

  private int myFlags = IS_ENABLED | IS_VISIBLE | IS_DISABLE_GROUP_IF_EMPTY;
  private @NotNull Supplier<@ActionDescription String> descriptionSupplier = NULL_STRING;
  private @NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier = () -> null;
  private @NotNull SmartFMap<String, Object> myUserMap = SmartFMap.emptyMap();

  private @Nullable Supplier<? extends @Nullable Icon> icon;
  private Icon disabledIcon;
  private Icon hoveredIcon;
  private Icon selectedIcon;

  private PropertyChangeSupport changeSupport;
  private double myWeight = DEFAULT_WEIGHT;

  private static final @NotNull NotNullLazyValue<Boolean> removeMnemonics = NotNullLazyValue.createValue(() -> {
    return SystemInfoRt.isMac && DynamicBundle.LanguageBundleEP.EP_NAME.hasAnyExtensions();
  });

  public static Presentation newTemplatePresentation() {
    Presentation presentation = new Presentation();
    presentation.myFlags = BitUtil.set(presentation.myFlags, IS_TEMPLATE, true);
    return presentation;
  }

  public Presentation() {
  }

  public Presentation(@NotNull @ActionText String text) {
    TextWithMnemonic textWithMnemonic = TextWithMnemonic.fromPlainText(text);
    textWithMnemonicSupplier = () -> textWithMnemonic;
  }

  public Presentation(@NotNull Supplier<@ActionText String> dynamicText) {
    textWithMnemonicSupplier = () -> TextWithMnemonic.fromPlainText(dynamicText.get());
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    PropertyChangeSupport support = changeSupport;
    if (support == null) {
      changeSupport = support = new PropertyChangeSupport(this);
    }
    support.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    PropertyChangeSupport support = changeSupport;
    if (support != null) {
      support.removePropertyChangeListener(l);
    }
  }

  /**
   * DO NOT USE as <code>presentation1.setText(presentation2.getText())</code>,
   * this will skip mnemonic and might break for texts with <code>'_'</code> or <code>'&'</code> symbols.
   * <p>
   * Use <code>presentation1.setTextWithMnemonic(presentation2.getTextWithPossibleMnemonic())</code>
   * or  <code>presentation1.setText(presentation2.getTextWithMnemonic())</code> to copy text between two presentations.
   * Use <code>presentation1.setText(presentation2.getText(), false)</code> to copy text without mnemonic.
   * <p>
   * This applies to AnAction constructors.
   *
   * @return Text without mnemonic.
   */
  public @ActionText String getText() {
    return getText(false);
  }

  public @ActionText String getText(boolean withSuffix) {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.getText(withSuffix);
  }

  @ApiStatus.Internal
  public boolean hasText() {
    return textWithMnemonicSupplier.get() != null;
  }

  /**
   * Sets the presentation text.
   *
   * @param text               presentation text. Use it if you need to localize text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Title) Supplier<String> text, boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(text, mayContainMnemonic));
  }

  /**
   * Sets the presentation text.
   *
   * @param text               presentation text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@Nullable @ActionText String text, boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(() -> text, mayContainMnemonic));
  }

  public @NotNull Supplier<TextWithMnemonic> getTextWithMnemonic(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> text,
                                                                 boolean mayContainMnemonic) {
    if (mayContainMnemonic) {
      return () -> {
        String s = text.get();
        if (s == null) {
          return null;
        }
        TextWithMnemonic parsed = TextWithMnemonic.parse(s);
        UISettings uiSettings = UISettings.getInstanceOrNull();
        boolean mnemonicsDisabled = uiSettings != null && uiSettings.getDisableMnemonicsInControls();
        return mnemonicsDisabled ? parsed.dropMnemonic(removeMnemonics.getValue()) : parsed;
      };
    }
    else {
      return () -> {
        String s = text.get();
        return s == null ? null : TextWithMnemonic.fromPlainText(s);
      };
    }
  }

  /**
   * Sets the presentation text
   *
   * @param textWithMnemonicSupplier text with mnemonic to set
   */
  public void setTextWithMnemonic(@NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier) {
    if (changeSupport == null) {
      this.textWithMnemonicSupplier = textWithMnemonicSupplier;
      return;
    }

    String oldText = getText();
    String oldTextWithSuffix = getText(true);
    int oldMnemonic = getMnemonic();
    int oldIndex = getDisplayedMnemonicIndex();
    this.textWithMnemonicSupplier = textWithMnemonicSupplier;

    fireObjectPropertyChange(PROP_TEXT, oldText, getText());
    fireObjectPropertyChange(PROP_TEXT_WITH_SUFFIX, oldTextWithSuffix, getText(true));
    fireObjectPropertyChange(PROP_MNEMONIC_KEY, oldMnemonic, getMnemonic());
    fireObjectPropertyChange(PROP_MNEMONIC_INDEX, oldIndex, getDisplayedMnemonicIndex());
  }

  /**
   * Sets the text with mnemonic.
   *
   * @see #setText(String, boolean)
   */
  public void setText(@Nullable @ActionText String text) {
    setText(() -> text, true);
  }

  /**
   * Sets the text with mnemonic supplier. Use it if you need to localize text.
   */
  public void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Title) Supplier<String> text) {
    setText(text, true);
  }

  @ApiStatus.Internal
  public void setFallbackPresentationText(@NotNull Supplier<String> supplier) {
    Supplier<TextWithMnemonic> original = textWithMnemonicSupplier;
    Supplier<TextWithMnemonic> fallback = getTextWithMnemonic(supplier, true);
    textWithMnemonicSupplier = () -> {
      TextWithMnemonic result = original.get();
      return result == null ? fallback.get() : result;
    };
  }

  /**
   * @return the text with mnemonic, properly escaped, so it could be passed to {@link #setText(String)} (e.g. to copy the presentation).
   */
  public @ActionText @Nullable String getTextWithMnemonic() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.toString();
  }

  public @NotNull Supplier<TextWithMnemonic> getTextWithPossibleMnemonic() {
    return textWithMnemonicSupplier;
  }

  public void restoreTextWithMnemonic(Presentation presentation) {
    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
  }

  public @ActionDescription String getDescription() {
    return descriptionSupplier.get();
  }

  public void setDescription(@NotNull Supplier<@ActionDescription String> dynamicDescription) {
    Supplier<String> oldDescription = descriptionSupplier;
    descriptionSupplier = dynamicDescription;
    if (changeSupport != null) {
      fireObjectPropertyChange(PROP_DESCRIPTION, oldDescription.get(), descriptionSupplier.get());
    }
  }

  public void setDescription(@ActionDescription String description) {
    Supplier<String> oldDescriptionSupplier = descriptionSupplier;
    descriptionSupplier = () -> description;
    if (changeSupport != null) {
      fireObjectPropertyChange(PROP_DESCRIPTION, oldDescriptionSupplier.get(), description);
    }
  }

  public Icon getIcon() {
    Supplier<? extends Icon> icon = this.icon;
    return icon == null ? null : icon.get();
  }

  public @Nullable Supplier<? extends @Nullable Icon> getIconSupplier() {
    return icon;
  }

  public void copyIconIfUnset(@NotNull Presentation other) {
    if (icon == null && other.icon != null) {
      icon = other.icon;
    }
  }

  public void setIcon(@Nullable Icon icon) {
    if (changeSupport == null) {
      this.icon = icon == null ? null : () -> icon;
      return;
    }

    Icon oldIcon = getIcon();
    this.icon = () -> icon;
    fireObjectPropertyChange(PROP_ICON, oldIcon, this.icon.get());
  }

  public void setIconSupplier(@Nullable Supplier<? extends @Nullable Icon> icon) {
    Supplier<? extends @Nullable Icon> oldIcon = this.icon;
    this.icon = icon;

    PropertyChangeSupport support = changeSupport;
    if (support != null) {
      Icon icon1 = oldIcon == null ? null : oldIcon.get();
      Icon icon2 = icon == null ? null : icon.get();
      if (!Objects.equals(icon1, icon2)) {
        support.firePropertyChange(PROP_ICON, icon1, icon2);
      }
    }
  }

  public Icon getDisabledIcon() {
    return disabledIcon;
  }

  public void setDisabledIcon(@Nullable Icon icon) {
    Icon oldDisabledIcon = disabledIcon;
    disabledIcon = icon;
    fireObjectPropertyChange(PROP_DISABLED_ICON, oldDisabledIcon, disabledIcon);
  }

  public Icon getHoveredIcon() {
    return hoveredIcon;
  }

  public void setHoveredIcon(final @Nullable Icon hoveredIcon) {
    Icon old = this.hoveredIcon;
    this.hoveredIcon = hoveredIcon;
    fireObjectPropertyChange(PROP_HOVERED_ICON, old, this.hoveredIcon);
  }

  public Icon getSelectedIcon() {
    return selectedIcon;
  }

  public void setSelectedIcon(Icon selectedIcon) {
    Icon old = this.selectedIcon;
    this.selectedIcon = selectedIcon;
    fireObjectPropertyChange(PROP_SELECTED_ICON, old, this.selectedIcon);
  }

  /**
   * @return an extended key code for a mnemonic character, or {@code KeyEvent.VK_UNDEFINED} if mnemonic is not set
   */
  public int getMnemonic() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? 0 : textWithMnemonic.getMnemonicCode();
  }

  /**
   * @return a mnemonic index in the whole text, or {@code -1} if mnemonic is not set
   */
  public int getDisplayedMnemonicIndex() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? -1 : textWithMnemonic.getMnemonicIndex();
  }

  /** @see Presentation#setVisible(boolean) */
  public boolean isVisible() {
    return BitUtil.isSet(myFlags, IS_VISIBLE);
  }

  /**
   * Sets whether the action is visible in menus, toolbars and popups or not.
   */
  public void setVisible(boolean visible) {
    assertNotTemplatePresentation();
    boolean oldVisible = BitUtil.isSet(myFlags, IS_VISIBLE);
    myFlags = BitUtil.set(myFlags, IS_VISIBLE, visible);
    fireBooleanPropertyChange(PROP_VISIBLE, oldVisible, visible);
  }

  /** @see Presentation#setPopupGroup(boolean) */
  public boolean isPopupGroup() {
    return BitUtil.isSet(myFlags, IS_POPUP_GROUP);
  }

  /**
   * For an action group presentation sets whether the action group is a popup group or not.
   * A popup action group is shown as a submenu, a toolbar button that shows a popup when clicked, etc.
   * A non-popup action group child actions are injected into the parent group.
   */
  public void setPopupGroup(boolean popup) {
    myFlags = BitUtil.set(myFlags, IS_POPUP_GROUP, popup);
  }

  /** @see Presentation#setPerformGroup(boolean) */
  public boolean isPerformGroup() {
    return BitUtil.isSet(myFlags, IS_PERFORM_GROUP);
  }

  /**
   * For an action group presentation sets whether the action group is "performable" as an ordinary action or not.
   *
   * @see com.intellij.openapi.actionSystem.impl.ActionMenu#SUPPRESS_SUBMENU
   * @see com.intellij.openapi.actionSystem.impl.ActionButton#HIDE_DROPDOWN_ICON
   */
  public void setPerformGroup(boolean performing) {
    myFlags = BitUtil.set(myFlags, IS_PERFORM_GROUP, performing);
  }

  /** @see Presentation#setHideGroupIfEmpty(boolean) */
  public boolean isHideGroupIfEmpty() {
    return BitUtil.isSet(myFlags, IS_HIDE_GROUP_IF_EMPTY);
  }

  /**
   * For an action group presentation sets whether the action group will be hidden if no visible children are present.
   * The default is {@code false}.
   */
  public void setHideGroupIfEmpty(boolean hide) {
    myFlags = BitUtil.set(myFlags, IS_HIDE_GROUP_IF_EMPTY, hide);
  }

  /** @see Presentation#setHideGroupIfEmpty(boolean) */
  public boolean isDisableGroupIfEmpty() {
    return BitUtil.isSet(myFlags, IS_DISABLE_GROUP_IF_EMPTY);
  }

  /**
   * For an action group presentation sets whether the action group will be shown as disabled if no visible children are present.
   * The default is {@code true}.
   */
  public void setDisableGroupIfEmpty(boolean disable) {
    myFlags = BitUtil.set(myFlags, IS_DISABLE_GROUP_IF_EMPTY, disable);
  }

  /**
   * Template presentations must be returned by {@link AnAction#getTemplatePresentation()} only.
   * Template presentations assert that their enabled and visible flags are never updated
   * because menus and shortcut processing use different defaults,
   * so values from template presentations are silently ignored.
   */
  public boolean isTemplate() {
    return BitUtil.isSet(myFlags, IS_TEMPLATE);
  }

  /** @see Presentation#setEnabled(boolean) */
  public boolean isEnabled() {
    return BitUtil.isSet(myFlags, IS_ENABLED);
  }

  /**
   * Sets whether the action is enabled or not. If an action is disabled, {@link AnAction#actionPerformed} is not be called.
   * In case when action represents a button or a menu item, the representing button or item will be greyed out.
   */
  public void setEnabled(boolean enabled) {
    assertNotTemplatePresentation();
    boolean oldEnabled = BitUtil.isSet(myFlags, IS_ENABLED);
    myFlags = BitUtil.set(myFlags, IS_ENABLED, enabled);
    fireBooleanPropertyChange(PROP_ENABLED, oldEnabled, enabled);
  }

  public void setEnabledAndVisible(boolean enabled) {
    setEnabled(enabled);
    setVisible(enabled);
  }

  private void fireBooleanPropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    PropertyChangeSupport support = changeSupport;
    if (oldValue != newValue && support != null) {
      support.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  private void fireObjectPropertyChange(String propertyName, Object oldValue, Object newValue) {
    PropertyChangeSupport support = changeSupport;
    if (support != null && !Objects.equals(oldValue, newValue)) {
      support.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  void assertNotTemplatePresentation() {
    if (BitUtil.isSet(myFlags, IS_TEMPLATE)) {
      LOG.warnInProduction(new Throwable("Template presentations must not be used directly"));
    }
  }

  @Override
  public Presentation clone() {
    try {
      Presentation clone = (Presentation)super.clone();
      clone.myFlags = BitUtil.set(clone.myFlags, IS_TEMPLATE, false);
      clone.changeSupport = null;
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public void copyFrom(@NotNull Presentation presentation) {
    copyFrom(presentation, null, false, false);
  }

  public void copyFrom(@NotNull Presentation presentation, @Nullable Component customComponent) {
    copyFrom(presentation, customComponent, true, false);
  }

  public void copyFrom(@NotNull Presentation presentation, @Nullable Component customComponent, boolean allFlags) {
    copyFrom(presentation, customComponent, true, allFlags);
  }

  private void copyFrom(@NotNull Presentation presentation,
                        @Nullable Component customComponent,
                        boolean forceNullComponent,
                        boolean allFlags) {
    if (presentation == this) {
      return;
    }

    boolean oldEnabled = isEnabled(), oldVisible = isVisible();
    if (allFlags) {
      myFlags = BitUtil.set(presentation.myFlags, IS_TEMPLATE, isTemplate());
    }
    else {
      myFlags = BitUtil.set(myFlags, IS_ENABLED, BitUtil.isSet(presentation.myFlags, IS_ENABLED));
      myFlags = BitUtil.set(myFlags, IS_VISIBLE, BitUtil.isSet(presentation.myFlags, IS_VISIBLE));
    }
    fireBooleanPropertyChange(PROP_ENABLED, oldEnabled, isEnabled());
    fireBooleanPropertyChange(PROP_VISIBLE, oldVisible, isVisible());

    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
    setDescription(presentation.descriptionSupplier);

    setIconSupplier(presentation.icon);

    setSelectedIcon(presentation.getSelectedIcon());
    setDisabledIcon(presentation.getDisabledIcon());
    setHoveredIcon(presentation.getHoveredIcon());
    setWeight(presentation.getWeight());

    if (!myUserMap.equals(presentation.myUserMap)) {
      Set<String> allKeys = new HashSet<>(presentation.myUserMap.keySet());
      allKeys.addAll(myUserMap.keySet());
      if (!allKeys.isEmpty()) {
        for (String key : allKeys) {
          if (key.equals(CustomComponentAction.COMPONENT_KEY.toString()) && (customComponent != null || forceNullComponent)) {
            putClientProperty(key, customComponent);
          }
          else {
            putClientProperty(key, presentation.getClientProperty(key));
          }
        }
      }
    }
  }

  public @Nullable <T> T getClientProperty(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myUserMap.get(key.toString());
  }

  public <T> void putClientProperty(@NotNull Key<T> key, @Nullable T value) {
    putClientProperty(key.toString(), value);
  }

  /** @deprecated Use {@link #getClientProperty(Key)} instead */
  @Deprecated
  public @Nullable Object getClientProperty(@NonNls @NotNull String key) {
    return myUserMap.get(key);
  }

  /** @deprecated Use {@link #putClientProperty(Key, Object)} instead */
  @Deprecated
  public void putClientProperty(@NonNls @NotNull String key, @Nullable Object value) {
    Object oldValue;
    synchronized (this) {
      oldValue = myUserMap.get(key);
      if (Comparing.equal(oldValue, value)) return;
      if (key.equals(CustomComponentAction.COMPONENT_KEY.toString()) && oldValue != null) {
        LOG.error("Trying to reset custom component in a presentation", new Throwable());
      }
      myUserMap = value == null ? myUserMap.minus(key) : myUserMap.plus(key, value);
    }
    fireObjectPropertyChange(key, oldValue, value);
  }

  public double getWeight() {
    return myWeight;
  }

  /**
   * Some action groups (like 'New...') may filter out actions with non-highest priority.
   *
   * @param weight please use {@link #HIGHER_WEIGHT} or {@link #EVEN_HIGHER_WEIGHT}
   */
  public void setWeight(double weight) {
    myWeight = weight;
  }

  public boolean isEnabledAndVisible() {
    return isEnabled() && isVisible();
  }

  /**
   * Sets if multiple actions or toggles can be performed in the same menu or popup.
   */
  public void setMultiChoice(boolean b) {
    myFlags = BitUtil.set(myFlags, IS_MULTI_CHOICE, b);
  }

  public boolean isMultiChoice() {
    return BitUtil.isSet(myFlags, IS_MULTI_CHOICE);
  }

  @Override
  public @Nls String toString() {
    return getText() + " (" + descriptionSupplier.get() + ")";
  }
}
