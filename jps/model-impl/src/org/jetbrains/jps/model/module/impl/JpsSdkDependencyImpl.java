// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkDependency;

final class JpsSdkDependencyImpl extends JpsDependencyElementBase<JpsSdkDependencyImpl> implements JpsSdkDependency {
  private final JpsSdkType<?> mySdkType;

  JpsSdkDependencyImpl(@NotNull JpsSdkType<?> sdkType) {
    super();
    mySdkType = sdkType;
  }

  JpsSdkDependencyImpl(JpsSdkDependencyImpl original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @Override
  public @NotNull JpsSdkDependencyImpl createCopy() {
    return new JpsSdkDependencyImpl(this);
  }

  @Override
  public @NotNull JpsSdkType<?> getSdkType() {
    return mySdkType;
  }

  @Override
  public JpsLibrary resolveSdk() {
    final JpsSdkReference<?> reference = getSdkReference();
    if (reference == null) return null;
    return reference.resolve();
  }

  @Override
  public @Nullable JpsSdkReference<?> getSdkReference() {
    return getContainingModule().getSdkReference(mySdkType);
  }

  @Override
  public String toString() {
    return "sdk dep [" + mySdkType + "]";
  }
}
