/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class PsiFileNode extends BasePsiNode<PsiFile> implements NavigatableWithText {

  public PsiFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    VirtualFile jarRoot = getJarRoot();
    if (jarRoot != null) {
      PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(jarRoot);
      if (psiDirectory != null) {
        return ProjectViewDirectoryHelper.getInstance(getProject()).getDirectoryChildren(psiDirectory, getSettings(), true);
      }
    }

    return new ArrayList<AbstractTreeNode>();
  }

  private boolean isArchive() {
    VirtualFile file = getVirtualFile();
    return file != null && file.isValid() && file.getFileType() instanceof ArchiveFileType;
  }

  protected void updateImpl(PresentationData data) {
    final PsiFile value = getValue();
    data.setPresentableText(value.getName());
    data.setIcons(value.getIcon(Iconable.ICON_FLAG_READ_STATUS));
  }

  public VirtualFile getVirtualFile() {
    PsiFile value = getValue();
    return value != null ? value.getVirtualFile() : null;
  }

  @Override
  public boolean canNavigate() {
    return isNavigatableLibraryRoot() || super.canNavigate();
  }

  private boolean isNavigatableLibraryRoot() {
    VirtualFile jarRoot = getJarRoot();
    final Project project = getProject();
    if (jarRoot != null && ProjectRootsUtil.isLibraryRoot(jarRoot, project)) {
      final OrderEntry orderEntry = LibraryUtil.findLibraryEntry(jarRoot, project);
      return orderEntry != null && ProjectSettingsService.getInstance(project).canOpenLibraryOrSdkSettings(orderEntry);
    }
    return false;
  }

  @Nullable
  private VirtualFile getJarRoot() {
    final VirtualFile file = getVirtualFile();
    if (file == null || !file.isValid() || !(file.getFileType() instanceof ArchiveFileType)) {
      return null;
    }
    return JarFileSystem.getInstance().getJarRootForLocalFile(file);
  }

  @Override
  public void navigate(boolean requestFocus) {
    VirtualFile jarRoot = getJarRoot();
    final Project project = getProject();
    if (jarRoot != null && ProjectRootsUtil.isLibraryRoot(jarRoot, project)) {
      final OrderEntry orderEntry = LibraryUtil.findLibraryEntry(jarRoot, project);
      if (orderEntry != null) {
        ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(orderEntry);
        return;
      }
    }
    super.navigate(requestFocus);
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    if (isNavigatableLibraryRoot()) {
      return "Open Library Settings";
    }
    return null;
  }

  public int getWeight() {
    return 20;
  }

  @Override
  public String getTitle() {
    final PsiFile file = getValue();
    if (file != null) {
      return FileUtil.getLocationRelativeToUserHome(file.getVirtualFile().getPresentableUrl());
    }
    return super.getTitle();
  }

  @Override
  protected boolean isMarkReadOnly() {
    return true;
  }

  public Comparable getTypeSortKey() {
    String extension = extension(getValue());
    return extension == null ? null : new ExtensionSortKey(extension);
  }

  @Nullable
  public static String extension(final PsiFile file) {
    return file == null || file.getVirtualFile() == null ? null : file.getVirtualFile().getFileType().getDefaultExtension();
  }

  public static class ExtensionSortKey implements Comparable {
    private final String myExtension;

    public ExtensionSortKey(final String extension) {
      myExtension = extension;
    }

    public int compareTo(final Object o) {
      if (!(o instanceof ExtensionSortKey)) return 0;
      ExtensionSortKey rhs = (ExtensionSortKey) o;
      return myExtension.compareTo(rhs.myExtension);
    }
  }

  @Override
  public boolean shouldDrillDownOnEmptyElement() {
    final PsiFile file = getValue();
    return file != null && file.getFileType() == StdFileTypes.JAVA;
  }

  public boolean canRepresent(final Object element) {
    return super.canRepresent(element) || getValue() != null && getValue().getVirtualFile() == element;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return super.contains(file) || isArchive() && PathUtil.getLocalFile(file) == getVirtualFile();
  }
}
