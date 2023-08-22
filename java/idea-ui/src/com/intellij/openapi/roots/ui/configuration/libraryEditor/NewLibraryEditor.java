// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TriConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class NewLibraryEditor extends LibraryEditorBase {
  private String myLibraryName;
  private final MultiMap<OrderRootType, LightFilePointer> myRoots;
  private final Set<LightFilePointer> myExcludedRoots;
  private final MultiMap<OrderRootType, String> myJarDirectoryUrls = new MultiMap<>();
  private final MultiMap<OrderRootType, String> myJarDirectoryRecursiveUrls = new MultiMap<>();
  private LibraryType myType;
  private LibraryProperties myProperties;
  private boolean myKeepInvalidUrls = true;

  public NewLibraryEditor() {
    this(null, null);
  }

  public NewLibraryEditor(@Nullable LibraryType type, @Nullable LibraryProperties properties) {
    myType = type;
    myProperties = properties;
    myRoots = new MultiMap<>();
    myExcludedRoots = new LinkedHashSet<>();
  }

  public void setKeepInvalidUrls(boolean keepInvalidUrls) {
    myKeepInvalidUrls = keepInvalidUrls;
  }

  @Override
  public Collection<OrderRootType> getOrderRootTypes() {
    return myRoots.keySet();
  }

  @Override
  @Nullable
  public LibraryType<?> getType() {
    return myType;
  }

  @Override
  public void setType(@NotNull LibraryType<?> type) {
    myType = type;
  }

  @Override
  public LibraryProperties getProperties() {
    return myProperties;
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    myProperties = properties;
  }

  @Override
  public String getName() {
    return myLibraryName;
  }

  @Override
  public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
    return pointersToUrls(myRoots.get(rootType));
  }

  private static String[] pointersToUrls(Collection<? extends LightFilePointer> pointers) {
    List<String> urls = new ArrayList<>(pointers.size());
    for (LightFilePointer pointer : pointers) {
      urls.add(pointer.getUrl());
    }
    return ArrayUtilRt.toStringArray(urls);
  }

  @Override
  public VirtualFile @NotNull [] getFiles(@NotNull OrderRootType rootType) {
    List<VirtualFile> result = new ArrayList<>();
    for (LightFilePointer pointer : myRoots.get(rootType)) {
      final VirtualFile file = pointer.getFile();
      if (file == null) {
        continue;
      }

      if (file.isDirectory()) {
        final String url = file.getUrl();
        if (isJarDirectory(url, rootType)) {
          boolean recursive = myJarDirectoryRecursiveUrls.get(rootType).contains(url);
          collectJarFiles(file, result, recursive);
          continue;
        }
      }
      result.add(file);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public String @NotNull [] getExcludedRootUrls() {
    return pointersToUrls(myExcludedRoots);
  }

  @Override
  public void setName(String name) {
    myLibraryName = name;
  }

  @Override
  public void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(file));
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(@NotNull VirtualFile file, boolean recursive, @NotNull OrderRootType rootType) {
    addJarDirectory(file.getUrl(), recursive, rootType);
  }

  @Override
  public void addExcludedRoot(@NotNull String url) {
    myExcludedRoots.add(new LightFilePointer(url));
  }

  @Override
  public void removeExcludedRoot(@NotNull String url) {
    myExcludedRoots.remove(new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(@NotNull final String url, boolean recursive, @NotNull OrderRootType rootType) {
    addRoot(url, rootType);
    (recursive ? myJarDirectoryRecursiveUrls : myJarDirectoryUrls).putValue(rootType, url);
  }

  @Override
  public void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.remove(rootType, new LightFilePointer(url));
    myExcludedRoots.removeIf(pointer -> !isUnderRoots(pointer.getUrl()));
    myJarDirectoryUrls.remove(rootType, url);
    myJarDirectoryRecursiveUrls.remove(rootType, url);
  }

  private boolean isUnderRoots(@NotNull String url) {
    for (LightFilePointer pointer : myRoots.values()) {
      if (VfsUtilCore.isEqualOrAncestor(pointer.getUrl(), url)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasChanges() {
    return true;
  }

  @Override
  public boolean isJarDirectory(@NotNull String url, @NotNull OrderRootType rootType) {
    return myJarDirectoryUrls.get(rootType).contains(url) || myJarDirectoryRecursiveUrls.get(rootType).contains(url);
  }

  @Override
  public boolean isValid(@NotNull String url, @NotNull OrderRootType orderRootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(orderRootType);
    for (LightFilePointer pointer : pointers) {
      if (pointer.getUrl().equals(url)) {
        return pointer.isValid();
      }
    }
    return false;
  }

  public void applyTo(@NotNull LibraryEx.ModifiableModelEx model) {
    model.setProperties(myProperties);
    exportRoots(model::getUrls, model::isValid, model::removeRoot, model::addRoot, model::addJarDirectory, model::addExcludedRoot);
  }


  public void applyTo(@NotNull LibraryEditorBase editor) {
    editor.setProperties(myProperties);
    exportRoots(editor::getUrls, editor::isValid, editor::removeRoot, editor::addRoot, editor::addJarDirectory, editor::addExcludedRoot);
  }

  private void exportRoots(
    final Function<? super OrderRootType, String[]> getUrls,
    final BiPredicate<? super String, ? super OrderRootType> isValid,
    final BiConsumer<? super String, ? super OrderRootType> removeRoot,
    final BiConsumer<? super String, ? super OrderRootType> addRoot,
    final TriConsumer<? super String, ? super Boolean, ? super OrderRootType> addJarDir,
    final Consumer<? super String> addExcludedRoot) {

    // first, clean the target container optionally preserving invalid paths
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : getUrls.apply(type)) {
        if (!myKeepInvalidUrls || isValid.test(url, type)) {
          removeRoot.accept(url, type);
        }
      }
    }

    // apply editor's state to the target container
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        if (!isJarDirectory(pointer.getUrl(), type)) {
          addRoot.accept(pointer.getUrl(), type);
        }
      }
    }
    for (Map.Entry<OrderRootType, Collection<String>> entry : myJarDirectoryUrls.entrySet()) {
      OrderRootType type = entry.getKey();
      for (String url : entry.getValue()) {
        addJarDir.accept(url, false, type);
      }
    }
    for (Map.Entry<OrderRootType, Collection<String>> entry : myJarDirectoryRecursiveUrls.entrySet()) {
      OrderRootType type = entry.getKey();
      for (String url : entry.getValue()) {
        addJarDir.accept(url, true, type);
      }
    }
    for (LightFilePointer root: myExcludedRoots) {
      addExcludedRoot.accept(root.getUrl());
    }
  }

  private static void collectJarFiles(@NotNull VirtualFile dir, @NotNull List<? super VirtualFile> container, final boolean recursively) {
    VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<Void>(VirtualFileVisitor.SKIP_ROOT, recursively ? null : VirtualFileVisitor.ONE_LEVEL_DEEP) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence()) == ArchiveFileType.INSTANCE) {
          VirtualFile jarRoot = StandardFileSystems.jar().findFileByPath(file.getPath() + URLUtil.JAR_SEPARATOR);
          if (jarRoot != null) {
            container.add(jarRoot);
            return false;
          }
        }
        return true;
      }
    });
  }
}
