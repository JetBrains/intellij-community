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
  private static final JpsElementCollectionKind<JpsLibraryRootImpl> LIBRARY_ROOTS_COLLECTION = new JpsElementCollectionKind<JpsLibraryRootImpl>(JpsLibraryRootKind.INSTANCE);
  private static final JpsTypedDataKind<JpsLibraryType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsLibraryType<?>>();

  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<?> type, @NotNull JpsModel model,
                        @NotNull JpsEventDispatcher eventDispatcher, JpsElementCollection<JpsLibraryImpl> parent) {
    super(model, eventDispatcher, name, parent);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsLibraryType<?>>(type, eventDispatcher, this));
    myContainer.setChild(LIBRARY_ROOTS_COLLECTION);
  }

  public JpsLibraryImpl(@NotNull JpsLibraryImpl original, JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(original, model, eventDispatcher, parent);
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
    getRootsCollection().addChild(new JpsLibraryRootImpl(getEventDispatcher(), url, rootType, this));
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

  public JpsElementCollectionImpl<JpsLibraryImpl> getParent() {
    //noinspection unchecked
    return (JpsElementCollectionImpl<JpsLibraryImpl>)myParent;
  }

  @NotNull
  @Override
  public JpsLibraryImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsLibraryImpl(this, model, eventDispatcher, parent);
  }

  @NotNull
  @Override
  public JpsLibraryReference createReference(JpsParentElement parent) {
    //noinspection unchecked
    final JpsElementReference<JpsCompositeElement> parentReference = ((JpsReferenceableElement<JpsCompositeElement>)getParent().getParent()).createReference(parent);
    return new JpsLibraryReferenceImpl(myModel, getEventDispatcher(), getName(), parentReference, parent);
  }
}
