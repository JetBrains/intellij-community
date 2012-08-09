package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public class JpsSdkReferenceImpl extends JpsNamedElementReferenceBase<JpsLibrary, JpsSdkReferenceImpl> implements JpsLibraryReference {
  @NotNull private final JpsSdkType<?> mySdkType;

  public JpsSdkReferenceImpl(@NotNull String elementName, @NotNull JpsSdkType<?> sdkType, @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE, elementName, parentReference);
    mySdkType = sdkType;
  }

  public JpsSdkReferenceImpl(JpsSdkReferenceImpl original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @NotNull
  @Override
  public String getLibraryName() {
    return myElementName;
  }

  @Override
  protected boolean resolvesTo(JpsLibrary element) {
    return super.resolvesTo(element) && element.getType().equals(mySdkType);
  }

  @NotNull
  @Override
  public JpsSdkReferenceImpl createCopy() {
    return new JpsSdkReferenceImpl(this);
  }

  @Override
  public JpsLibraryReference asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
