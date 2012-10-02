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

/**
 * @author nik
 */
public class JpsSdkReferenceImpl<P extends JpsElement> extends JpsNamedElementReferenceBase<JpsLibrary, JpsTypedLibrary<JpsSdk<P>>, JpsSdkReferenceImpl<P>> implements JpsSdkReference<P> {
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

  @NotNull
  public String getSdkName() {
    return myElementName;
  }

  @Override
  protected JpsTypedLibrary<JpsSdk<P>> resolve(JpsLibrary element) {
    return element.asTyped(mySdkType);
  }

  @NotNull
  @Override
  public JpsSdkReferenceImpl<P> createCopy() {
    return new JpsSdkReferenceImpl<P>(this);
  }

  @Nullable
  protected JpsElementCollection<? extends JpsLibrary> getCollection(@NotNull JpsCompositeElement parent) {
    return parent.getContainer().getChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE);
  }
}
