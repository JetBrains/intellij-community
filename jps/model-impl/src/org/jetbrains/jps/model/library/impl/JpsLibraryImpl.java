package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryImpl extends JpsNamedCompositeElementBase<JpsLibraryImpl, JpsProjectImpl> implements JpsLibrary {
  private static final JpsElementCollectionKind<JpsLibraryRoot> LIBRARY_ROOTS_COLLECTION =
    new JpsElementCollectionKind<JpsLibraryRoot>(JpsLibraryRootKind.INSTANCE);
  private static final JpsTypedDataKind<JpsLibraryType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsLibraryType<?>>();

  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<?> type) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsLibraryType<?>>(type));
    myContainer.setChild(LIBRARY_ROOTS_COLLECTION);
  }

  private JpsLibraryImpl(@NotNull JpsLibraryImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType) {
    return getRootsCollection().getElements();
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType) {
    addRoot(url, rootType, JpsLibraryRoot.InclusionOptions.ROOT_ITSELF);
  }

  @Override
  public void addRoot(@NotNull final String url, @NotNull final JpsOrderRootType rootType,
                      @NotNull JpsLibraryRoot.InclusionOptions options) {
    getRootsCollection().addChild(new JpsLibraryRootImpl(url, rootType, options));
  }

  private JpsElementCollectionImpl<JpsLibraryRoot> getRootsCollection() {
    return myContainer.getChild(LIBRARY_ROOTS_COLLECTION);
  }

  @Override
  public void removeUrl(@NotNull final String url, @NotNull final JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = getRootsCollection();
    for (JpsLibraryRoot root : rootsCollection.getElements()) {
      if (root.getUrl().equals(url) && root.getRootType().equals(rootType)) {
        rootsCollection.removeChild(root);
        break;
      }
    }
  }

  @Override
  public void delete() {
    getParent().removeChild(this);
  }

  public JpsElementCollectionImpl<JpsLibrary> getParent() {
    //noinspection unchecked
    return (JpsElementCollectionImpl<JpsLibrary>)myParent;
  }

  @NotNull
  @Override
  public JpsLibraryImpl createCopy() {
    return new JpsLibraryImpl(this);
  }

  @NotNull
  @Override
  public JpsLibraryReference createReference() {
    //noinspection unchecked
    final JpsElementReference<JpsCompositeElement> parentReference =
      ((JpsReferenceableElement<JpsCompositeElement>)getParent().getParent()).createReference();
    return new JpsLibraryReferenceImpl(getName(), parentReference);
  }
}
