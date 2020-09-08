/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ToggleInvertedBooleanProperty extends ToggleBooleanProperty {
  public ToggleInvertedBooleanProperty(@NlsActions.ActionText String text,
                                       @NlsActions.ActionDescription String description,
                                       Icon icon,
                                       AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
    super(text, description, icon, properties, property);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return !getProperty().get(getProperties()).booleanValue();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    getProperty().set(getProperties(), Boolean.valueOf(!state));
  }

}
