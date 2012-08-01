package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsModuleImpl extends JpsNamedCompositeElementBase<JpsModuleImpl> implements JpsModule {
  private static final JpsTypedDataKind<JpsModuleType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsModuleType<?>>();
  private static final JpsUrlListKind CONTENT_ROOTS_KIND = new JpsUrlListKind("content roots");
  private static final JpsUrlListKind EXCLUDED_ROOTS_KIND = new JpsUrlListKind("excluded roots");
  public static final JpsElementKind<JpsDependenciesListImpl> DEPENDENCIES_LIST_KIND = JpsElementKindBase.create("dependencies");
  private final JpsLibraryCollection myLibraryCollection;

  public <P extends JpsElementProperties> JpsModuleImpl(JpsModuleType<P> type, @NotNull String name, @NotNull P properties) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsModuleType<?>>(type, properties));
    myContainer.setChild(CONTENT_ROOTS_KIND);
    myContainer.setChild(EXCLUDED_ROOTS_KIND);
    myContainer.setChild(JpsFacetKind.COLLECTION_KIND);
    myContainer.setChild(DEPENDENCIES_LIST_KIND, new JpsDependenciesListImpl());
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
    myContainer.setChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND);
    myContainer.setChild(JpsSdkReferencesTableImpl.KIND);
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

  @Override
  @NotNull
  public JpsElementProperties getProperties() {
    return myContainer.getChild(TYPED_DATA_KIND).getProperties();
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
  public List<JpsModuleSourceRoot> getSourceRoots() {
    return myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND).getElements();
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties, T extends JpsModuleSourceRootType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull T rootType) {
    return addSourceRoot(url, rootType, rootType.createDefaultProperties());
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties> JpsModuleSourceRoot addSourceRoot(@NotNull String url,
                                                                            @NotNull JpsModuleSourceRootType<P> rootType,
                                                                            @NotNull P properties) {
    final JpsModuleSourceRootImpl root = new JpsModuleSourceRootImpl(url, rootType, properties);
    myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND).addChild(root);
    root.setProperties(rootType, properties);
    return root;
  }

  @Override
  public void removeSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType rootType) {
    final JpsElementCollectionImpl<JpsModuleSourceRoot> roots = myContainer.getChild(JpsModuleSourceRootKind.ROOT_COLLECTION_KIND);
    for (JpsModuleSourceRoot root : roots.getElements()) {
      if (root.getRootType().equals(rootType) && root.getUrl().equals(url)) {
        roots.removeChild(root);
        break;
      }
    }
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties> JpsFacet addFacet(@NotNull String name, @NotNull JpsFacetType<P> type, @NotNull P properties) {
    return myContainer.getChild(JpsFacetKind.COLLECTION_KIND).addChild(new JpsFacetImpl(type, name, properties));
  }

  @NotNull
  @Override
  public List<JpsFacet> getFacets() {
    return myContainer.getChild(JpsFacetKind.COLLECTION_KIND).getElements();
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
  public JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type) {
    JpsLibraryReference sdkReference = getSdkReferencesTable().getSdkReference(type);
    if (sdkReference != null) {
      return sdkReference;
    }
    JpsProject project = getProject();
    if (project != null) {
      return project.getSdkReferencesTable().getSdkReference(type);
    }
    return null;
  }

  @Override
  public <P extends JpsSdkProperties> JpsTypedLibrary<P> getSdk(@NotNull JpsSdkType<P> type) {
    final JpsLibraryReference reference = getSdkReference(type);
    return reference != null ? (JpsTypedLibrary<P>)reference.resolve() : null;
  }

  @Override
  public void delete() {
    //noinspection unchecked
    ((JpsElementCollection<JpsModule>)myParent).removeChild(this);
  }

  @NotNull
  @Override
  public JpsModuleReference createReference() {
    return new JpsModuleReferenceImpl(getName());
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties, Type extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addModuleLibrary(@NotNull String name, @NotNull Type type) {
    return myLibraryCollection.addLibrary(name, type);
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

  @Nullable
  public JpsProject getProject() {
    JpsModel model = getModel();
    return model != null ? model.getProject() : null;
  }

  @Override
  public JpsModuleType<?> getModuleType() {
    return myContainer.getChild(TYPED_DATA_KIND).getType();
  }
}
