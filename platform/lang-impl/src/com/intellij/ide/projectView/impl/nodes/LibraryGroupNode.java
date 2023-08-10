// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class LibraryGroupNode extends ProjectViewNode<LibraryGroupElement> {
  public LibraryGroupNode(Project project, @NotNull LibraryGroupElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    Module module = getValue().getModule();
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (final OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry) {
        final Library library = libraryOrderEntry.getLibrary();
        if (library == null) {
          continue;
        }
        final String libraryName = library.getName();
        if (libraryName == null || libraryName.length() == 0) {
          addLibraryChildren(libraryOrderEntry, children, getProject(), this);
        }
        else {
          children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(module, libraryOrderEntry), getSettings()));
        }
      }
      else if (orderEntry instanceof JdkOrderEntry jdkOrderEntry) {
        final Sdk jdk = jdkOrderEntry.getJdk();
        if (jdk != null) {
          children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(module, jdkOrderEntry), getSettings()));
        }
      }
    }
    return children;
  }

  public static void addLibraryChildren(LibraryOrSdkOrderEntry entry, List<? super AbstractTreeNode<?>> children, Project project, ProjectViewNode node) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    VirtualFile[] files =
      entry instanceof LibraryOrderEntry ? getLibraryRoots((LibraryOrderEntry)entry) : entry.getRootFiles(OrderRootType.CLASSES);
    for (final VirtualFile file : files) {
      if (!file.isValid()) continue;
      if (file.isDirectory()) {
        final PsiDirectory psiDir = psiManager.findDirectory(file);
        if (psiDir == null) {
          continue;
        }
        children.add(new PsiDirectoryNode(project, psiDir, node.getSettings()));
      }
      else {
        final PsiFile psiFile = psiManager.findFile(file);
        if (psiFile == null) continue;
        children.add(new PsiFileNode(project, psiFile, node.getSettings()));
      }
    }
  }


  @Override
  public String getTestPresentation() {
    return "Libraries";
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (!index.isInLibrary(file)) {
      return false;
    }

    return someChildContainsFile(file, false);
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.libraries"));
    presentation.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Override
  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myProject).canOpenModuleLibrarySettings();
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Module module = getValue().getModule();
    ProjectSettingsService.getInstance(myProject).openModuleLibrarySettings(module);
  }

  public static VirtualFile @NotNull [] getLibraryRoots(@NotNull LibraryOrderEntry orderEntry) {
    Library library = orderEntry.getLibrary();
    if (library == null) return VirtualFile.EMPTY_ARRAY;
    OrderRootType[] rootTypes = LibraryType.DEFAULT_EXTERNAL_ROOT_TYPES;
    if (library instanceof LibraryEx) {
      if (((LibraryEx)library).isDisposed()) return VirtualFile.EMPTY_ARRAY;
      PersistentLibraryKind<?> libKind = ((LibraryEx)library).getKind();
      if (libKind != null) {
        rootTypes = LibraryType.findByKind(libKind).getExternalRootTypes();
      }
    }
    final ArrayList<VirtualFile> files = new ArrayList<>();
    for (OrderRootType rootType : rootTypes) {
      files.addAll(Arrays.asList(library.getFiles(rootType)));
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }
}
