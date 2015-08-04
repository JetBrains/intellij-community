/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.config;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

import javax.swing.*;

public class ToggleBooleanProperty extends ToggleAction {
  private final AbstractProperty.AbstractPropertyContainer myProperties;
  private final AbstractProperty<Boolean> myProperty;

  public ToggleBooleanProperty(String text, String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
    super(text, description, icon);
    myProperties = properties;
    myProperty = property;
  }

  public boolean isSelected(AnActionEvent e) {
    return myProperty.get(myProperties).booleanValue();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    myProperty.set(myProperties, Boolean.valueOf(state));
  }

  protected AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  protected AbstractProperty<Boolean> getProperty() {
    return myProperty;
  }

  public static abstract class Disablable extends ToggleBooleanProperty {
    public Disablable(String text, String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
      super(text, description, icon, properties, property);
    }

    protected abstract boolean isEnabled();
    protected abstract boolean isVisible();

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
      e.getPresentation().setVisible(isVisible());
    }
  }
}
