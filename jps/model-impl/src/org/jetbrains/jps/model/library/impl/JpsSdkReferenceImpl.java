// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

public final class JpsSdkReferenceImpl<P extends JpsElement> extends JpsNamedElementReferenceBase<JpsLibrary, JpsTypedLibrary<JpsSdk<P>>, JpsSdkReferenceImpl<P>> implements JpsSdkReference<P> {
  private final JpsSdkType<P> mySdkType;

  public JpsSdkReferenceImpl(@NotNull String elementName, @NotNull JpsSdkType<P> sdkType,
                             @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(elementName, parentReference);
    mySdkType = sdkType;
  }

  private JpsSdkReferenceImpl(JpsSdkReferenceImpl<P> original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @Override
  public @NotNull String getSdkName() {
    return myElementName;
  }

  @Override
  protected JpsTypedLibrary<JpsSdk<P>> resolve(JpsLibrary element) {
    return element.asTyped(mySdkType);
  }

  @Override
  public @NotNull JpsSdkReferenceImpl<P> createCopy() {
    return new JpsSdkReferenceImpl<>(this);
  }

  @Override
  protected @Nullable JpsElementCollection<? extends JpsLibrary> getCollection(@NotNull JpsCompositeElement parent) {
    return parent.getContainer().getChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE);
  }

  @Override
  public String toString() {
    return "SDK reference: '" + myElementName + "' in " + getParentReference();
  }
}
