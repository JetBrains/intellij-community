package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkDependency;

/**
 * @author nik
 */
public class JpsSdkDependencyImpl extends JpsDependencyElementBase<JpsSdkDependencyImpl> implements JpsSdkDependency {
  private final JpsSdkType<?> mySdkType;
  
  public JpsSdkDependencyImpl(@NotNull JpsSdkType<?> sdkType, JpsModel model, JpsEventDispatcher eventDispatcher, JpsDependenciesListImpl parent) {
    super(model, eventDispatcher, parent);
    mySdkType = sdkType;
  }

  public JpsSdkDependencyImpl(JpsSdkDependencyImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
    mySdkType = original.mySdkType;
  }

  @NotNull
  @Override
  public JpsSdkDependencyImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsSdkDependencyImpl(this, model, eventDispatcher, parent);
  }

  @Override
  @NotNull
  public JpsSdkType<?> getSdkType() {
    return mySdkType;
  }

  @Override
  public JpsLibrary resolveSdk() {
    final JpsLibraryReference reference = getParent().getParent().getSdkReferencesTable().getSdkReference(mySdkType);
    return reference != null ? reference.resolve() : null;
  }

  @Override
  public JpsDependenciesListImpl getParent() {
    return (JpsDependenciesListImpl)super.getParent();
  }
}
