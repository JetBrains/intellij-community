package org.jetbrains.jps.model.library.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.library.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryImpl<P extends JpsElementProperties> extends JpsNamedCompositeElementBase<JpsLibraryImpl<P>> implements JpsTypedLibrary<P> {
  private static final JpsTypedDataKind<JpsLibraryType<?>> TYPED_DATA_KIND = new JpsTypedDataKind<JpsLibraryType<?>>();

  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties) {
    super(name);
    myContainer.setChild(TYPED_DATA_KIND, new JpsTypedDataImpl<JpsLibraryType<?>>(type, properties));
  }

  private JpsLibraryImpl(@NotNull JpsLibraryImpl<P> original) {
    super(original);
  }

  @Override
  @NotNull
  public JpsLibraryType<?> getType() {
    return myContainer.getChild(TYPED_DATA_KIND).getType();
  }

  @NotNull
  @Override
  public P getProperties() {
    return (P)myContainer.getChild(TYPED_DATA_KIND).getProperties();
  }

  @NotNull
  @Override
  public List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getKind(rootType));
    return rootsCollection != null ? rootsCollection.getElements() : Collections.<JpsLibraryRoot>emptyList();
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType) {
    addRoot(url, rootType, JpsLibraryRoot.InclusionOptions.ROOT_ITSELF);
  }

  @Override
  public void addRoot(@NotNull final String url, @NotNull final JpsOrderRootType rootType,
                      @NotNull JpsLibraryRoot.InclusionOptions options) {
    myContainer.getOrSetChild(getKind(rootType)).addChild(new JpsLibraryRootImpl(url, rootType, options));
  }

  @Override
  public void removeUrl(@NotNull final String url, @NotNull final JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getKind(rootType));
    if (rootsCollection != null) {
      for (JpsLibraryRoot root : rootsCollection.getElements()) {
        if (root.getUrl().equals(url) && root.getRootType().equals(rootType)) {
          rootsCollection.removeChild(root);
          break;
        }
      }
    }
  }

  private static JpsElementCollectionKind<JpsLibraryRoot> getKind(JpsOrderRootType type) {
    return new JpsElementCollectionKind<JpsLibraryRoot>(new JpsLibraryRootKind(type));
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
  public JpsLibraryImpl<P> createCopy() {
    return new JpsLibraryImpl<P>(this);
  }

  @NotNull
  @Override
  public JpsLibraryReference createReference() {
    //noinspection unchecked
    final JpsElementReference<JpsCompositeElement> parentReference =
      ((JpsReferenceableElement<JpsCompositeElement>)getParent().getParent()).createReference();
    return new JpsLibraryReferenceImpl(getName(), parentReference);
  }

  @Override
  public List<File> getFiles(final JpsOrderRootType rootType) {
    List<File> files = new ArrayList<File>();
    for (JpsLibraryRoot root : getRoots(rootType)) {
      final File file = JpsPathUtil.urlToFile(root.getUrl());
      switch (root.getInclusionOptions()) {
        case ROOT_ITSELF:
          files.add(file);
          break;
        case ARCHIVES_UNDER_ROOT:
          collectArchives(file, false, files);
          break;
        case ARCHIVES_UNDER_ROOT_RECURSIVELY:
          collectArchives(file, true, files);
          break;
      }
    }
    return files;
  }

  private static void collectArchives(File file, boolean recursively, Collection<File> result) {
    final File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        final String extension = FileUtilRt.getExtension(child.getName());
        if (child.isDirectory()) {
          if (recursively) {
            collectArchives(child, recursively, result);
          }
        }
        else if (extension.equals("jar") || extension.equals("zip")) {
          result.add(child);
        }
      }
    }
  }
}
