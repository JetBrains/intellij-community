// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;

final class JpsWrappedJavaSdkReferenceImpl extends JpsCompositeElementBase<JpsWrappedJavaSdkReferenceImpl> implements JpsSdkReference<JpsDummyElement> {
  private static final JpsElementChildRoleBase<JpsSdkReference<?>> WRAPPER_ROLE = JpsElementChildRoleBase.create("wrapper reference");
  private final JpsJavaSdkTypeWrapper mySdkType;

  JpsWrappedJavaSdkReferenceImpl(JpsJavaSdkTypeWrapper sdkType, JpsSdkReference<?> wrapperReference) {
    mySdkType = sdkType;
    myContainer.setChild(WRAPPER_ROLE, wrapperReference);
  }

  private JpsWrappedJavaSdkReferenceImpl(JpsWrappedJavaSdkReferenceImpl original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @Override
  public @NotNull JpsWrappedJavaSdkReferenceImpl createCopy() {
    return new JpsWrappedJavaSdkReferenceImpl(this);
  }

  @Override
  public @NotNull String getSdkName() {
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> sdk = resolve();
    return sdk != null ? sdk.getName() : "<unknown>";
  }

  @Override
  public @Nullable JpsTypedLibrary<JpsSdk<JpsDummyElement>> resolve() {
    JpsTypedLibrary<? extends JpsSdk<? extends JpsElement>> wrapper = myContainer.getChild(WRAPPER_ROLE).resolve();
    if (wrapper == null) return null;
    JpsModel model = getModel();
    if (model == null) return null;
    String sdkName = mySdkType.getJavaSdkName(wrapper.getProperties().getSdkProperties());
    if (sdkName == null) return null;

    JpsLibrary library = model.getGlobal().getLibraryCollection().findLibrary(sdkName);
    return library != null ? library.asTyped(JpsJavaSdkType.INSTANCE) : null;
  }

  @Override
  public JpsElementReference<JpsTypedLibrary<JpsSdk<JpsDummyElement>>> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
