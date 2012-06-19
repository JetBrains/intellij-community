package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsModuleImpl extends JpsNamedCompositeElementBase<JpsModuleImpl, JpsProjectImpl> implements JpsModule {
  private static final JpsTypedDataKind<JpsModuleType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsModuleType<?>>();
  private static final JpsUrlListKind CONTENT_ROOTS_KIND = new JpsUrlListKind("content roots");
  private static final JpsUrlListKind EXCLUDED_ROOTS_KIND = new JpsUrlListKind("excluded roots");
  public static final JpsElementKind<JpsDependenciesListImpl> DEPENDENCIES_LIST_KIND = new JpsElementKindBase<JpsDependenciesListImpl>("dependencies");
  private final JpsLibraryCollection myLibraryCollection;

  public JpsModuleImpl(JpsModuleType type,
                       @NotNull String name) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsModuleType<?>>(type));
    myContainer.setChild(CONTENT_ROOTS_KIND);
    myContainer.setChild(EXCLUDED_ROOTS_KIND);
    myContainer.setChild(DEPENDENCIES_LIST_KIND, new JpsDependenciesListImpl());
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
    myContainer.setChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND);
    myContainer.setChild(JpsSdkReferencesTableImpl.KIND, new JpsSdkReferencesTableImpl());
  }

  private JpsModuleImpl(JpsModuleImpl original) {
    super(original);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
  }

  @NotNull
  @Override
  public JpsModuleImpl createCopy() {
    return new JpsModuleImpl(this);
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
    final JpsModuleSourceRootImpl root = new JpsModuleSourceRootImpl(url, rootType);
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
  public JpsModuleReference createReference() {
    return new JpsModuleReferenceImpl(getName());
  }

  @NotNull
  @Override
  public JpsLibrary addModuleLibrary(@NotNull JpsLibraryType<?> type, @NotNull String name) {
    return myLibraryCollection.addLibrary(type, name);
  }

  @Override
  public void addModuleLibrary(final @NotNull JpsLibrary library) {
    myLibraryCollection.addLibrary(library);
  }

  @NotNull
  @Override
  public JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }
}
