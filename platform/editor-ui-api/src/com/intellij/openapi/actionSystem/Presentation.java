/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.SmartFMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The presentation of an action in a specific place in the user interface.
 *
 * @see AnAction
 * @see ActionPlaces
 */
public final class Presentation implements Cloneable {
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

  public static final double DEFAULT_WEIGHT = 0;
  public static final double HIGHER_WEIGHT = 42;
  public static final double EVEN_HIGHER_WEIGHT = 239;

  private PropertyChangeSupport myChangeSupport;
  private String myText;
  private String myDescription;
  private Icon myIcon;
  private Icon myDisabledIcon;
  private Icon myHoveredIcon;
  private Icon mySelectedIcon;
  private int myMnemonic;
  private int myDisplayedMnemonicIndex = -1;
  private boolean myVisible = true;
  private boolean myEnabled = true;
  private double myWeight = DEFAULT_WEIGHT;

  public Presentation() {
  }

  public Presentation(String text) {
    myText = text;
  }

  public void addPropertyChangeListener(PropertyChangeListener l) {
    PropertyChangeSupport support = myChangeSupport;
    if (support == null) {
      support = myChangeSupport = new PropertyChangeSupport(this);
    }
    support.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(PropertyChangeListener l) {
    PropertyChangeSupport support = myChangeSupport;
    if (support != null) {
      support.removePropertyChangeListener(l);
    }
  }

  public String getText() {
    return myText;
  }

  public void setText(@Nullable String text, boolean mayContainMnemonic) {
    int oldMnemonic = myMnemonic;
    int oldDisplayedMnemonicIndex = myDisplayedMnemonicIndex;
    String oldText = myText;
    myMnemonic = 0;
    myDisplayedMnemonicIndex = -1;

    if (text != null) {
      if (text.indexOf(UIUtil.MNEMONIC) >= 0) {
        text = text.replace(UIUtil.MNEMONIC, '&');
      }

      if (mayContainMnemonic) {
        StringBuilder plainText = new StringBuilder();
        int backShift = 0;
        for (int i = 0; i < text.length(); i++) {
          char ch = text.charAt(i);
          if (myMnemonic == 0 && (ch == '_' || ch == '&')) {
            //noinspection AssignmentToForLoopParameter
            i++;
            if (i >= text.length()) break;
            ch = text.charAt(i);
            if (ch != '_' && ch != '&') {
              if (UISettings.getInstance().getDisableMnemonicsInControls()) {
                myMnemonic = 0;
                myDisplayedMnemonicIndex = -1;
              }
              else {
                myMnemonic = Character.toUpperCase(ch);  // mnemonics are case insensitive
                myDisplayedMnemonicIndex = i - 1 - backShift;
              }
            }
            else {
              backShift++;
            }
          }
          plainText.append(ch);
        }
        myText = plainText.length() == 0 ? "" : plainText.toString();
      }
      else {
        myText = text.isEmpty() ? "" : text;
      }
    }
    else {
      myText = null;
    }

    fireObjectPropertyChange(PROP_TEXT, oldText, myText);
    fireObjectPropertyChange(PROP_MNEMONIC_KEY, oldMnemonic, myMnemonic);
    fireObjectPropertyChange(PROP_MNEMONIC_INDEX, oldDisplayedMnemonicIndex, myDisplayedMnemonicIndex);
  }

  public void setText(String text) {
    setText(text, true);
  }

  public String getTextWithMnemonic() {
    if (myText != null && myDisplayedMnemonicIndex > -1) {
      return myText.substring(0, myDisplayedMnemonicIndex) + "_" + myText.substring(myDisplayedMnemonicIndex);
    }
    return myText;
  }

  public void restoreTextWithMnemonic(Presentation presentation) {
    setText(presentation.getTextWithMnemonic());
  }

  public static String restoreTextWithMnemonic(@Nullable String text, final int mnemonic) {
    if (text == null) {
      return null;
    }
    for (int i = 0; i < text.length(); i++) {
      if (Character.toUpperCase(text.charAt(i)) == mnemonic) {
        return text.substring(0, i) + "_" + text.substring(i);
      }
    }
    return text;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    String oldDescription = myDescription;
    myDescription = description;
    fireObjectPropertyChange(PROP_DESCRIPTION, oldDescription, myDescription);
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
    return myMnemonic;
  }

  public int getDisplayedMnemonicIndex() {
    return myDisplayedMnemonicIndex;
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

    if (!Objects.equals(myText, presentation.myText) ||
        myDisplayedMnemonicIndex != presentation.myDisplayedMnemonicIndex ||
        myMnemonic != presentation.myMnemonic) {
      setText(presentation.getTextWithMnemonic(), presentation.myDisplayedMnemonicIndex > -1);
    }
    setDescription(presentation.getDescription());
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
    return myText + " (" + myDescription + ")";
  }

  public boolean isEnabledAndVisible() {
    return isEnabled() && isVisible();
  }
}
