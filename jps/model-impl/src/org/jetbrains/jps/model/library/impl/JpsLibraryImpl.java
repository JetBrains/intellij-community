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
public class JpsLibraryImpl<P extends JpsElement> extends JpsNamedCompositeElementBase<JpsLibraryImpl<P>> implements JpsTypedLibrary<P> {
  private final JpsLibraryType<P> myLibraryType;
  
  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties) {
    super(name);
    myLibraryType = type;
    myContainer.setChild(myLibraryType.getPropertiesRole(), properties);
  }

  private JpsLibraryImpl(@NotNull JpsLibraryImpl<P> original) {
    super(original);
    myLibraryType = original.myLibraryType;
  }

  @Override
  @NotNull
  public JpsLibraryType<P> getType() {
    return myLibraryType;
  }

  @NotNull
  @Override
  public P getProperties() {
    return myContainer.getChild(myLibraryType.getPropertiesRole());
  }

  @NotNull
  @Override
  public List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getRole(rootType));
    return rootsCollection != null ? rootsCollection.getElements() : Collections.<JpsLibraryRoot>emptyList();
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType) {
    addRoot(url, rootType, JpsLibraryRoot.InclusionOptions.ROOT_ITSELF);
  }

  @Override
  public void addRoot(@NotNull File file, @NotNull JpsOrderRootType rootType) {
    addRoot(JpsPathUtil.getLibraryRootUrl(file), rootType);
  }

  @Override
  public void addRoot(@NotNull final String url, @NotNull final JpsOrderRootType rootType,
                      @NotNull JpsLibraryRoot.InclusionOptions options) {
    myContainer.getOrSetChild(getRole(rootType)).addChild(new JpsLibraryRootImpl(url, rootType, options));
  }

  @Override
  public void removeUrl(@NotNull final String url, @NotNull final JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getRole(rootType));
    if (rootsCollection != null) {
      for (JpsLibraryRoot root : rootsCollection.getElements()) {
        if (root.getUrl().equals(url) && root.getRootType().equals(rootType)) {
          rootsCollection.removeChild(root);
          break;
        }
      }
    }
  }

  private static JpsElementCollectionRole<JpsLibraryRoot> getRole(JpsOrderRootType type) {
    return JpsElementCollectionRole.create(new JpsLibraryRootRole(type));
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
    return new JpsLibraryReferenceImpl(getName(), createParentReference());
  }

  private JpsElementReference<JpsCompositeElement> createParentReference() {
    //noinspection unchecked
    return ((JpsReferenceableElement<JpsCompositeElement>)getParent().getParent()).createReference();
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
