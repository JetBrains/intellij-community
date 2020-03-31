// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NamedScope {
  public static final NamedScope[] EMPTY_ARRAY = new NamedScope[0];
  private final String myName;
  private final Icon myIcon;
  private final PackageSet myValue;

  public NamedScope(@NotNull @Nls String name, @Nullable PackageSet value) {
    this(name, AllIcons.Ide.LocalScope, value);
  }

  public NamedScope(@NotNull String name, @NotNull Icon icon, @Nullable PackageSet value) {
    myIcon = icon;
    myName = name;
    myValue = value;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public PackageSet getValue() {
    return myValue;
  }

  @NotNull
  public NamedScope createCopy() {
    return new NamedScope(myName, myIcon, myValue == null ? null : myValue.createCopy());
  }

  @Nullable
  public String getDefaultColorName() {
    return null;
  }

  public static class UnnamedScope extends NamedScope {
    public UnnamedScope(@NotNull PackageSet value) {
      super(value.getText(), value);
    }
  }

  @Override
  public String toString() {
    return "Scope '" + myName + "'; set:" + (myValue == null ? null : myValue.getText());
  }
}