package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public class JpsSdkReferenceImpl<P extends JpsElement> extends JpsNamedElementReferenceBase<JpsLibrary, JpsTypedLibrary<JpsSdk<P>>, JpsSdkReferenceImpl<P>> implements JpsSdkReference<P> {
  private final JpsSdkType<P> mySdkType;

  public JpsSdkReferenceImpl(@NotNull String elementName,
                             @NotNull JpsSdkType<P> sdkType,
                             @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE, elementName, parentReference);
    mySdkType = sdkType;
  }

  private JpsSdkReferenceImpl(JpsSdkReferenceImpl<P> original) {
    super(original);
    mySdkType = original.mySdkType;
  }

  @NotNull
  public String getSdkName() {
    return myElementName;
  }

  @Override
  protected JpsTypedLibrary<JpsSdk<P>> resolve(JpsLibrary element) {
    if (element.getType().equals(mySdkType)) {
      //noinspection unchecked
      return (JpsTypedLibrary<JpsSdk<P>>)element;
    }
    return null;
  }

  @NotNull
  @Override
  public JpsSdkReferenceImpl<P> createCopy() {
    return new JpsSdkReferenceImpl<P>(this);
  }

  @Override
  public JpsSdkReferenceImpl<P> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
