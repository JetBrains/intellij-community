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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class TextConfigurable<T> extends NamedConfigurable<T> {
  private final T myObject;
  private final String myBannerSlogan;
  private final String myDisplayName;
  private final Icon myClosedIcon;
  private final String myDescriptionText;

  public TextConfigurable(final T object,
                          final String displayName,
                          final String bannerSlogan,
                          final String descriptionText,
                          final Icon closedIcon) {
    myDisplayName = displayName;
    myBannerSlogan = bannerSlogan;
    myDescriptionText = descriptionText;
    myClosedIcon = closedIcon;
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
  public void reset() {
    //do nothing
  }

  @Override
  public void disposeUIResources() {
    //do nothing
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Override
  public T getEditableObject() {
    return myObject;
  }

  @Override
  public String getBannerSlogan() {
    return myBannerSlogan;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public Icon getIcon(final boolean open) {
    return myClosedIcon;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new PanelWithText(myDescriptionText);
  }
}
