package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
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
    final JpsLibraryReference reference = getSdkReference();
    return reference != null ? reference.resolve() : null;
  }

  @Override
  @Nullable
  public JpsLibraryReference getSdkReference() {
    return getContainingModule().getSdkReference(mySdkType);
  }

  @Override
  public boolean isInherited() {
    return false;
  }
}
