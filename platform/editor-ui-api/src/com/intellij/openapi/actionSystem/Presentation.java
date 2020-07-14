/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.util.SmartFMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * The presentation of an action in a specific place in the user interface.
 *
 * @see AnAction
 * @see ActionPlaces
 */
public final class Presentation implements Cloneable {
  public static final Supplier<String> NULL_STRING = () -> null;

  private SmartFMap<String, Object> myUserMap = SmartFMap.emptyMap();

  /**
   * Defines tool tip for button at tool bar or text for element at menu
   * value: String
   */
  @NonNls public static final String PROP_TEXT = "text";
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
  /**
   * value: Boolean
   */
  @NonNls public static final String STRIP_MNEMONIC = "stripMnemonic";

  public static final double DEFAULT_WEIGHT = 0;
  public static final double HIGHER_WEIGHT = 42;
  public static final double EVEN_HIGHER_WEIGHT = 239;

  private PropertyChangeSupport myChangeSupport;
  @NotNull private Supplier<String> myDescriptionSupplier = () -> null;
  private Icon myIcon;
  private Icon myDisabledIcon;
  private Icon myHoveredIcon;
  private Icon mySelectedIcon;
  @NotNull private Supplier<TextWithMnemonic> myTextWithMnemonicSupplier = () -> null;
  private boolean myVisible = true;
  private boolean myEnabled = true;
  private boolean myMultipleChoice = false;
  private double myWeight = DEFAULT_WEIGHT;

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

  private static final Pattern MNEMONIC = Pattern.compile(" ?\\(_?[A-Z]\\)");
  
  public String getText() {
    TextWithMnemonic textWithMnemonic = myTextWithMnemonicSupplier.get();
    String text = textWithMnemonic == null ? null : textWithMnemonic.getText();

    if (text != null && Boolean.TRUE.equals(getClientProperty(STRIP_MNEMONIC))) {
      Matcher matcher = MNEMONIC.matcher(text);
      return matcher.replaceAll("");
    }
    return text;
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
  public Supplier<TextWithMnemonic> getTextWithMnemonic(@Nls(capitalization = Nls.Capitalization.Title) @NotNull Supplier<String> text,
                                                        boolean mayContainMnemonic) {
    Supplier<TextWithMnemonic> textWithMnemonic;
    if (mayContainMnemonic) {
      textWithMnemonic = () -> Optional.ofNullable(text.get()).map(TextWithMnemonic::parse).orElse(null);

      UISettings uiSettings = UISettings.getInstanceOrNull();
      if (uiSettings != null && uiSettings.getDisableMnemonicsInControls()) {
        Supplier<TextWithMnemonic> finalTextWithMnemonic = textWithMnemonic;
        textWithMnemonic = () -> finalTextWithMnemonic.get().dropMnemonic();
      }
    }
    else {
      textWithMnemonic = () -> Optional.ofNullable(text.get()).map(TextWithMnemonic::fromPlainText).orElse(null);
    }
    return textWithMnemonic;
  }

  /**
   * Sets the presentation text
   * @param textWithMnemonicSupplier text with mnemonic to set
   */
  public void setTextWithMnemonic(@NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier) {
    String oldText = getText();
    int oldMnemonic = getMnemonic();
    int oldIndex = getDisplayedMnemonicIndex();
    myTextWithMnemonicSupplier = textWithMnemonicSupplier;

    fireObjectPropertyChange(PROP_TEXT, oldText, getText());
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

  public static String restoreTextWithMnemonic(@Nullable String text, final int mnemonic) {
    if (text == null) return null;
    TextWithMnemonic textWithMnemonic = TextWithMnemonic.fromPlainText(text);
    for (int i = 0; i < text.length(); i++) {
      if (Character.toUpperCase(text.charAt(i)) == mnemonic) {
        return textWithMnemonic.setMnemonicAt(i).toString();
      }
    }
    return textWithMnemonic.toString();
  }

  public String getDescription() {
    return myDescriptionSupplier.get();
  }

  public void setDescription(@NotNull Supplier<String> dynamicDescription) {
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

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    boolean oldVisible = myVisible;
    myVisible = visible;
    fireBooleanPropertyChange(PROP_VISIBLE, oldVisible, myVisible);
  }

  /**
   * Returns the state of this action.
   *
   * @return {@code true} if action is enabled, {@code false} otherwise
   */
  public boolean isEnabled() {
    return myEnabled;
  }

  /**
   * Sets whether the action enabled or not. If an action is disabled, {@link AnAction#actionPerformed}
   * won't be called. In case when action represents a button or a menu item, the
   * representing button or item will be greyed out.
   *
   * @param enabled {@code true} if you want to enable action, {@code false} otherwise
   */
  public void setEnabled(boolean enabled) {
    boolean oldEnabled = myEnabled;
    myEnabled = enabled;
    fireBooleanPropertyChange(PROP_ENABLED, oldEnabled, myEnabled);
  }

  public final void setEnabledAndVisible(boolean enabled) {
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

  @Override
  public Presentation clone() {
    try {
      Presentation clone = (Presentation)super.clone();
      clone.myChangeSupport = null;
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public void copyFrom(Presentation presentation) {
    if (presentation == this) return;

    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
    setDescription(presentation.myDescriptionSupplier);
    setIcon(presentation.getIcon());
    setSelectedIcon(presentation.getSelectedIcon());
    setDisabledIcon(presentation.getDisabledIcon());
    setHoveredIcon(presentation.getHoveredIcon());
    setVisible(presentation.isVisible());
    setEnabled(presentation.isEnabled());
    setWeight(presentation.getWeight());

    if (!myUserMap.equals(presentation.myUserMap)) {
      Set<String> allKeys = new HashSet<>(presentation.myUserMap.keySet());
      allKeys.addAll(myUserMap.keySet());
      if (!allKeys.isEmpty()) {
        for (String key : allKeys) {
          putClientProperty(key, presentation.getClientProperty(key));
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

  @Nullable
  public Object getClientProperty(@NonNls @NotNull String key) {
    return myUserMap.get(key);
  }

  public void putClientProperty(@NonNls @NotNull String key, @Nullable Object value) {
    Object oldValue = myUserMap.get(key);
    if (Comparing.equal(oldValue, value)) return;
    myUserMap = value == null ? myUserMap.minus(key) : myUserMap.plus(key, value);
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
    this.myMultipleChoice = b;
  }

  public boolean isMultipleChoice(){
    return myMultipleChoice;
  }
}
