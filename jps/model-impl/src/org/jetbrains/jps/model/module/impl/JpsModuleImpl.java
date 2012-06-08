package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsModuleImpl extends JpsNamedCompositeElementBase<JpsModuleImpl, JpsProjectImpl> implements JpsModule {
  private static final JpsTypedDataKind<JpsModuleType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsModuleType<?>>();
  private static final JpsElementKind<JpsUrlListImpl> CONTENT_ROOTS_KIND = new JpsElementKind<JpsUrlListImpl>();
  private static final JpsElementKind<JpsUrlListImpl> EXCLUDED_ROOTS_KIND = new JpsElementKind<JpsUrlListImpl>();
  public static final JpsElementKind<JpsDependenciesListImpl> DEPENDENCIES_LIST_KIND = new JpsElementKind<JpsDependenciesListImpl>();

  public JpsModuleImpl(JpsModel model, JpsEventDispatcher eventDispatcher, JpsModuleType type, @NotNull String name, JpsElementCollectionImpl<JpsModuleImpl> parent) {
    super(model, eventDispatcher, name, parent);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsModuleType<?>>(type, eventDispatcher, this));
    myContainer.setChild(CONTENT_ROOTS_KIND, new JpsUrlListImpl(eventDispatcher, this));
    myContainer.setChild(EXCLUDED_ROOTS_KIND, new JpsUrlListImpl(eventDispatcher, this));
    myContainer.setChild(DEPENDENCIES_LIST_KIND, new JpsDependenciesListImpl(model, eventDispatcher, this));
    myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    myContainer.setChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND);
    myContainer.setChild(JpsSdkReferencesTableImpl.KIND, new JpsSdkReferencesTableImpl(model, eventDispatcher, this));
  }

  public JpsModuleImpl(JpsModuleImpl original, JpsEventDispatcher eventDispatcher, JpsModel model, JpsParentElement parent) {
    super(original, model, eventDispatcher, parent);
  }

  @NotNull
  @Override
  public JpsModuleImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsModuleImpl(this, eventDispatcher, model, parent);
  }

  @NotNull
  @Override
  public JpsUrlList getContentRootsList() {
    return myContainer.getChild(CONTENT_ROOTS_KIND);
  }

  @NotNull
  public JpsUrlList getExcludeRootsList() {
    return myContainer.getChild(EXCLUDED_ROOTS_KIND);
  }

  @NotNull
  @Override
  public List<? extends JpsModuleSourceRootImpl> getSourceRoots() {
    return myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND).getElements();
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties> JpsModuleSourceRoot addSourceRoot(@NotNull JpsModuleSourceRootType<P> rootType,
                                                                            @NotNull String url) {
    return addSourceRoot(rootType, url, rootType.createDefaultProperties());
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties> JpsModuleSourceRoot addSourceRoot(@NotNull JpsModuleSourceRootType<P> rootType,
                                                                            @NotNull String url,
                                                                            @NotNull P properties) {
    final JpsModuleSourceRootImpl root = new JpsModuleSourceRootImpl(myModel, getEventDispatcher(), url, rootType, this);
    myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND).addChild(root);
    root.setProperties(rootType, properties);
    return root;
  }

  @Override
  public void removeSourceRoot(@NotNull JpsModuleSourceRootType rootType, @NotNull String url) {
    final JpsElementCollectionImpl<JpsModuleSourceRootImpl> roots = myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND);
    for (JpsModuleSourceRootImpl root : roots.getElements()) {
      if (root.getRootType().equals(rootType) && root.getUrl().equals(url)) {
        roots.removeChild(root);
        break;
      }
    }
  }

  @NotNull
  @Override
  public JpsDependenciesList getDependenciesList() {
    return myContainer.getChild(DEPENDENCIES_LIST_KIND);
  }

  @Override
  @NotNull
  public JpsSdkReferencesTable getSdkReferencesTable() {
    return myContainer.getChild(JpsSdkReferencesTableImpl.KIND);
  }

  @Override
  public void delete() {
    //noinspection unchecked
    ((JpsElementCollectionImpl<JpsModuleImpl>)myParent).removeChild(this);
  }

  @NotNull
  @Override
  public JpsModuleReference createReference(JpsParentElement parent) {
    return new JpsModuleReferenceImpl(myModel, getName(), getEventDispatcher(), parent);
  }

  @NotNull
  @Override
  public JpsLibrary addModuleLibrary(@NotNull JpsLibraryType<?> type, @NotNull String name) {
    final JpsElementCollectionImpl<JpsLibraryImpl> collection = myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    return collection.addChild(new JpsLibraryImpl(name, type, myModel, getEventDispatcher(), collection));
  }
}
