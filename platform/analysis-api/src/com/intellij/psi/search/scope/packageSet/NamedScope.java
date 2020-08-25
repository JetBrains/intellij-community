// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public class NamedScope {
  public static final NamedScope[] EMPTY_ARRAY = new NamedScope[0];
  private final @NonNls String myName;
  private final @NotNull Supplier<@NlsSafe String> myPresentableNameSupplier;
  private final Icon myIcon;
  private final PackageSet myValue;

  public NamedScope(@NotNull @NonNls String name, @Nullable PackageSet value) {
    this(name, AllIcons.Ide.LocalScope, value);
  }

  public NamedScope(@NotNull @NonNls String name, @NotNull Icon icon, @Nullable PackageSet value) {
    this(name, () -> name, icon, value);
  }

  public NamedScope(@NotNull @NonNls String name, @NotNull Supplier <@NlsSafe String> presentableNameSupplier, @NotNull Icon icon, @Nullable PackageSet value) {
    myPresentableNameSupplier = presentableNameSupplier;
    myIcon = icon;
    myName = name;
    myValue = value;
  }

  @NonNls
  public String getName() {
    return myName;
  }

  @NlsSafe
  public String getPresentableName() {
    return myPresentableNameSupplier.get();
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
    return new NamedScope(myName, myPresentableNameSupplier, myIcon, myValue == null ? null : myValue.createCopy());
  }

  @Nullable
  public String getDefaultColorName() {
    return null;
  }

  public static class UnnamedScope extends NamedScope {
    public UnnamedScope(@NotNull PackageSet value) {
      super(value.getText(), () -> value.getText(), AllIcons.Ide.LocalScope, value);
    }
  }

  @Override
  public String toString() {
    return "Scope '" + myName + "'; set:" + (myValue == null ? null : myValue.getText());
  }
}