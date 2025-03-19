// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class JavaDependentSdkType extends DependentSdkType implements JavaSdkType {

  public JavaDependentSdkType(@NonNls @NotNull String name) {
    super(name);
  }

  @Override
  protected boolean isValidDependency(@NotNull Sdk sdk) {
    return sdk.getSdkType() instanceof JavaSdkType;
  }

  @Override
  public @NotNull String getUnsatisfiedDependencyMessage() {
    return JavaBundle.message("dependant.sdk.unsatisfied.dependency.message");
  }

  @Override
  public @NotNull SdkType getDependencyType() {
    return JavaSdk.getInstance();
  }

  @Override
  public boolean isDependent() {
    return true;
  }
}
