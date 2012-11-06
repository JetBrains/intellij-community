package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkDependency;

/**
 * @author nik
 */
public class JpsSdkDependencyImpl extends JpsDependencyElementBase<JpsSdkDependencyImpl> implements JpsSdkDependency {
  private final JpsSdkType<?> mySdkType;

  public JpsSdkDependencyImpl(@NotNull JpsSdkType<?> sdkType) {
    super();
    mySdkType = sdkType;
  }

  public JpsSdkDependencyImpl(JpsSdkDependencyImpl original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @NotNull
  @Override
  public JpsSdkDependencyImpl createCopy() {
    return new JpsSdkDependencyImpl(this);
  }

  @Override
  @NotNull
  public JpsSdkType<?> getSdkType() {
    return mySdkType;
  }

  @Override
  public JpsLibrary resolveSdk() {
    final JpsSdkReference<?> reference = getSdkReference();
    if (reference == null) return null;
    return reference.resolve();
  }

  @Override
  @Nullable
  public JpsSdkReference<?> getSdkReference() {
    return getContainingModule().getSdkReference(mySdkType);
  }

  @Override
  public boolean isInherited() {
    return false;
  }

  @Override
  public String toString() {
    return "sdk dep [" + mySdkType + "]";
  }
}
