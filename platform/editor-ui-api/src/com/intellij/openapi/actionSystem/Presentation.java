// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Defines tool tip for button at toolbar or text for element at menu
   * value: String
   */
  @NonNls public static final String PROP_TEXT = "text";
  /**
   * Defines tool tip for button at toolbar or text for element at menu
   * that includes mnemonic suffix, like "Git(G)"
   * value: String
   */
  @NonNls public static final String PROP_TEXT_WITH_SUFFIX = "textWithSuffix";
  /**
   * value: Integer
   */
  @NonNls public static final String PROP_MNEMONIC_KEY = "mnemonicKey";
  /**
   * value: Integer
   */
  @NonNls public static final String PROP_MNEMONIC_INDEX = "mnemonicIndex";
  /**
   * value: String
   */
  @NonNls public static final String PROP_DESCRIPTION = "description";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_ICON = "icon";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_DISABLED_ICON = "disabledIcon";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_SELECTED_ICON = "selectedIcon";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_HOVERED_ICON = "hoveredIcon";
  /**
   * value: Boolean
   */
  @NonNls public static final String PROP_VISIBLE = "visible";
  /**
   * The actual value is a Boolean.
   */
  @NonNls public static final String PROP_ENABLED = "enabled";

  @NonNls public static final Key<@Nls String> PROP_VALUE = Key.create("value");

  public static final double DEFAULT_WEIGHT = 0;
  public static final double HIGHER_WEIGHT = 42;
  public static final double EVEN_HIGHER_WEIGHT = 239;

  private static final int IS_ENABLED = 0x1;
  private static final int IS_VISIBLE = 0x2;
  private static final int IS_MULTI_CHOICE = 0x4;
  private static final int IS_POPUP_GROUP = 0x10;
  private static final int IS_PERFORM_GROUP = 0x20;
  private static final int IS_TEMPLATE = 0x1000;

  private int myFlags = IS_ENABLED | IS_VISIBLE;
  private @NotNull Supplier<@ActionDescription String> myDescriptionSupplier = () -> null;
  private @NotNull Supplier<TextWithMnemonic> myTextWithMnemonicSupplier = () -> null;
  private @NotNull SmartFMap<String, Object> myUserMap = SmartFMap.emptyMap();

  private Icon myIcon;
  private Icon myDisabledIcon;
  private Icon myHoveredIcon;
  private Icon mySelectedIcon;

  private PropertyChangeSupport myChangeSupport;
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
    myTextWithMnemonicSupplier = () -> TextWithMnemonic.fromPlainText(text);
  }

  public Presentation(@NotNull Supplier<@ActionText String> dynamicText) {
    myTextWithMnemonicSupplier = () -> TextWithMnemonic.fromPlainText(dynamicText.get());
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    PropertyChangeSupport support = myChangeSupport;
    if (support == null) {
      myChangeSupport = support = new PropertyChangeSupport(this);
    }
    support.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    PropertyChangeSupport support = myChangeSupport;
    if (support != null) {
      support.removePropertyChangeListener(l);
    }
  }

  public @ActionText String getText() {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.getText();
  }

  public @ActionText String getText(boolean withSuffix) {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.getText(withSuffix);
  }

  /**
   * Sets the presentation text.
   *
   * @param text presentation text. Use it if you need to localize text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Title) Supplier<String> text,
                      boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(text, mayContainMnemonic));
  }

  /**
   * Sets the presentation text.
   *
   * @param text presentation text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@Nullable @ActionText String text, boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(() -> text, mayContainMnemonic));
  }

  @NotNull
  public Supplier<TextWithMnemonic> getTextWithMnemonic(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> text,
                                                        boolean mayContainMnemonic) {
    if (mayContainMnemonic) {
      return () -> {
        String s = text.get();
        if (s == null) return null;
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
   * @param textWithMnemonicSupplier text with mnemonic to set
   */
  public void setTextWithMnemonic(@NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier) {
    String oldText = getText();
    String oldTextWithSuffix = getText(true);
    int oldMnemonic = getMnemonic();
    int oldIndex = getDisplayedMnemonicIndex();
    myTextWithMnemonicSupplier = textWithMnemonicSupplier;

    fireObjectPropertyChange(PROP_TEXT, oldText, getText());
    fireObjectPropertyChange(PROP_TEXT_WITH_SUFFIX, oldTextWithSuffix, getText(true));
    fireObjectPropertyChange(PROP_MNEMONIC_KEY, oldMnemonic, getMnemonic());
    fireObjectPropertyChange(PROP_MNEMONIC_INDEX, oldIndex, getDisplayedMnemonicIndex());
  }

  /**
   * Sets the text with mnemonic.
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

  /**
   * @return the text with mnemonic, properly escaped, so it could be passed to {@link #setText(String)} (e.g. to copy the presentation).
   */
  @ActionText
  @Nullable
  public String getTextWithMnemonic() {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.toString();
  }

  @NotNull
  public Supplier<TextWithMnemonic> getTextWithPossibleMnemonic() {
    return myTextWithMnemonicSupplier;
  }

  public void restoreTextWithMnemonic(Presentation presentation) {
    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
  }

  public @ActionDescription String getDescription() {
    return myDescriptionSupplier.get();
  }

  public void setDescription(@NotNull Supplier<@ActionDescription String> dynamicDescription) {
    Supplier<String> oldDescription = myDescriptionSupplier;
    myDescriptionSupplier = dynamicDescription;
    fireObjectPropertyChange(PROP_DESCRIPTION, oldDescription.get(), myDescriptionSupplier.get());
  }

  public void setDescription(@ActionDescription String description) {
    Supplier<String> oldDescriptionSupplier = myDescriptionSupplier;
    myDescriptionSupplier = () -> description;
    fireObjectPropertyChange(PROP_DESCRIPTION, oldDescriptionSupplier.get(), description);
  }

  public Icon getIcon() {
    return myIcon;
  }

  public void setIcon(@Nullable Icon icon) {
    Icon oldIcon = myIcon;
    myIcon = icon;
    fireObjectPropertyChange(PROP_ICON, oldIcon, myIcon);
  }

  public Icon getDisabledIcon() {
    return myDisabledIcon;
  }

  public void setDisabledIcon(@Nullable Icon icon) {
    Icon oldDisabledIcon = myDisabledIcon;
    myDisabledIcon = icon;
    fireObjectPropertyChange(PROP_DISABLED_ICON, oldDisabledIcon, myDisabledIcon);
  }

  public Icon getHoveredIcon() {
    return myHoveredIcon;
  }

  public void setHoveredIcon(@Nullable final Icon hoveredIcon) {
    Icon old = myHoveredIcon;
    myHoveredIcon = hoveredIcon;
    fireObjectPropertyChange(PROP_HOVERED_ICON, old, myHoveredIcon);
  }

  public Icon getSelectedIcon() {
    return mySelectedIcon;
  }

  public void setSelectedIcon(Icon selectedIcon) {
    Icon old = mySelectedIcon;
    mySelectedIcon = selectedIcon;
    fireObjectPropertyChange(PROP_SELECTED_ICON, old, mySelectedIcon);
  }

  public int getMnemonic() {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    return textWithMnemonic == null ? 0 : textWithMnemonic.getMnemonic();
  }

  public int getDisplayedMnemonicIndex() {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    return textWithMnemonic == null ? -1 : textWithMnemonic.getMnemonicIndex();
  }

  /** @see Presentation#setVisible(boolean)  */
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
   * A non-popup action group child actions are injected into the group parent group.
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
   */
  public void setPerformGroup(boolean performing) {
    myFlags = BitUtil.set(myFlags, IS_PERFORM_GROUP, performing);
  }

  /**
   * Template presentations must be returned by {@link AnAction#getTemplatePresentation()} only.
   * Template presentations assert that their enabled and visible flags are never updated
   * because menus and shortcut processing use different defaults,
   * so values from template presentations are silently ignored.
   */
  boolean isTemplate() {
    return BitUtil.isSet(myFlags, IS_TEMPLATE);
  }

  /** @see Presentation#setEnabled(boolean)  */
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
    PropertyChangeSupport support = myChangeSupport;
    if (oldValue != newValue && support != null) {
      support.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  private void fireObjectPropertyChange(String propertyName, Object oldValue, Object newValue) {
    PropertyChangeSupport support = myChangeSupport;
    if (support != null && !Objects.equals(oldValue, newValue)) {
      support.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  private void assertNotTemplatePresentation() {
    if (BitUtil.isSet(myFlags, IS_TEMPLATE)) {
      LOG.warn(new Throwable("Shall not be called on a template presentation"));
    }
  }

  @Override
  public Presentation clone() {
    try {
      Presentation clone = (Presentation)super.clone();
      clone.myFlags = BitUtil.set(clone.myFlags, IS_TEMPLATE, false);
      clone.myChangeSupport = null;
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
    if (presentation == this) return;
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
    setDescription(presentation.myDescriptionSupplier);
    setIcon(presentation.getIcon());
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

  @Nullable
  public <T> T getClientProperty(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myUserMap.get(key.toString());
  }

  public <T> void putClientProperty(@NotNull Key<T> key, @Nullable T value) {
    putClientProperty(key.toString(), value);
  }

  /** @deprecated Use {@link #getClientProperty(Key)} instead */
  @Deprecated
  @Nullable
  public Object getClientProperty(@NonNls @NotNull String key) {
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
   * @param weight please use {@link #HIGHER_WEIGHT} or {@link #EVEN_HIGHER_WEIGHT}
   */
  public void setWeight(double weight) {
    myWeight = weight;
  }

  @Override
  @Nls
  public String toString() {
    return getText() + " (" + myDescriptionSupplier.get() + ")";
  }

  public boolean isEnabledAndVisible() {
    return isEnabled() && isVisible();
  }

  /**
   * This parameter specifies if multiple actions can be taken in the same context
   */
  public void setMultipleChoice(boolean b) {
    myFlags = BitUtil.set(myFlags, IS_MULTI_CHOICE, b);
  }

  public boolean isMultipleChoice(){
    return BitUtil.isSet(myFlags, IS_MULTI_CHOICE);
  }
}
