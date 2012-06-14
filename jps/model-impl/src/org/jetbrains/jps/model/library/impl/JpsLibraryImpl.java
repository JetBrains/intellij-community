package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryRootType;
import org.jetbrains.jps.model.library.JpsLibraryType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryImpl extends JpsNamedCompositeElementBase<JpsLibraryImpl, JpsProjectImpl> implements JpsLibrary {
  private static final JpsElementCollectionKind<JpsLibraryRootImpl> LIBRARY_ROOTS_COLLECTION =
    new JpsElementCollectionKind<JpsLibraryRootImpl>(JpsLibraryRootKind.INSTANCE);
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
  public List<String> getUrls(@NotNull JpsLibraryRootType rootType) {
    final List<String> urls = new ArrayList<String>();
    for (JpsLibraryRootImpl root : getRootsCollection().getElements()) {
      if (root.getRootType().equals(rootType)) {
        urls.add(root.getUrl());
      }
    }
    return urls;
  }

  @Override
  public void addUrl(@NotNull final String url, @NotNull final JpsLibraryRootType rootType) {
    getRootsCollection().addChild(new JpsLibraryRootImpl(url, rootType));
  }

  private JpsElementCollectionImpl<JpsLibraryRootImpl> getRootsCollection() {
    return myContainer.getChild(LIBRARY_ROOTS_COLLECTION);
  }

  @Override
  public void removeUrl(@NotNull final String url, @NotNull final JpsLibraryRootType rootType) {
    final JpsElementCollectionImpl<JpsLibraryRootImpl> rootsCollection = getRootsCollection();
    for (JpsLibraryRootImpl root : rootsCollection.getElements()) {
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
