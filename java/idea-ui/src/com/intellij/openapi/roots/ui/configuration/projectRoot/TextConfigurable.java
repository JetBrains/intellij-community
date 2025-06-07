// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TextConfigurable<T> extends NamedConfigurable<T> {
  private final @NotNull T myObject;
  private final @NotNull @Nls String myBannerSlogan;
  private final @NotNull @NlsContexts.ConfigurableName String myDisplayName;
  private final Icon myIcon;
  private final @NotNull @Nls String myDescriptionText;

  public TextConfigurable(@NotNull T object,
                          @NotNull @NlsContexts.ConfigurableName String displayName,
                          @NotNull @Nls String bannerSlogan,
                          @NotNull @NlsContexts.DetailedDescription String descriptionText,
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

  @Override
  public @NotNull String getBannerSlogan() {
    return myBannerSlogan;
  }

  @Override
  public @NotNull String getDisplayName() {
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
