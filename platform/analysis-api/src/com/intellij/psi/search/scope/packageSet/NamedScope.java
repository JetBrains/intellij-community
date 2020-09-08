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
  private final @NonNls String myScopeId;
  private final @NotNull Supplier<@NlsSafe String> myPresentableNameSupplier;
  private final Icon myIcon;
  private final PackageSet myValue;

  public NamedScope(@NotNull @NonNls String scopeId, @Nullable PackageSet value) {
    this(scopeId, AllIcons.Ide.LocalScope, value);
  }

  public NamedScope(@NotNull @NonNls String scopeId, @NotNull Icon icon, @Nullable PackageSet value) {
    this(scopeId, () -> scopeId, icon, value);
  }

  public NamedScope(@NotNull @NonNls String scopeId, @NotNull Supplier <@NlsSafe String> presentableNameSupplier, @NotNull Icon icon, @Nullable PackageSet value) {
    myPresentableNameSupplier = presentableNameSupplier;
    myIcon = icon;
    myScopeId = scopeId;
    myValue = value;
  }

  /**
   * @deprecated please use {@link NamedScope#getScopeId()} for search/serialization/mappings and {@link #getPresentableName()} to display in UI
   */
  @Deprecated
  @NonNls
  public String getName() {
    return myScopeId;
  }

  @NonNls
  public String getScopeId() {
    return myScopeId;
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
    return new NamedScope(myScopeId, myPresentableNameSupplier, myIcon, myValue == null ? null : myValue.createCopy());
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
    return "Scope '" + myScopeId + "'; set:" + (myValue == null ? null : myValue.getText());
  }
}