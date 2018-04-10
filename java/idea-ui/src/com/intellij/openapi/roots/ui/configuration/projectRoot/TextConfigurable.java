/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class TextConfigurable<T> extends NamedConfigurable<T> {
  @NotNull
  private final T myObject;
  @NotNull
  private final String myBannerSlogan;
  @NotNull
  private final String myDisplayName;
  private final Icon myIcon;
  @NotNull
  private final String myDescriptionText;

  public TextConfigurable(@NotNull T object,
                          @NotNull String displayName,
                          @NotNull String bannerSlogan,
                          @NotNull String descriptionText,
                          @Nullable Icon icon) {
    myDisplayName = displayName;
    myBannerSlogan = bannerSlogan;
    myDescriptionText = descriptionText;
    myIcon = icon;
    myObject = object;
  }

  @Override
  public void setDisplayName(final String name) {
    //do nothing
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    //do nothing
  }

  @Override
  public T getEditableObject() {
    return myObject;
  }

  @NotNull
  @Override
  public String getBannerSlogan() {
    return myBannerSlogan;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public Icon getIcon(final boolean open) {
    return myIcon;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new PanelWithText(myDescriptionText);
  }
}
