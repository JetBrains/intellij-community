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

import com.intellij.ide.IconProvider;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class PsiDirectoryNode extends BasePsiNode<PsiDirectory> implements NavigatableWithText {
  public PsiDirectoryNode(Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected void updateImpl(PresentationData data) {
    final Project project = getProject();
    final PsiDirectory psiDirectory = getValue();
    final VirtualFile directoryFile = psiDirectory.getVirtualFile();

    final Object parentValue = getParentValue();
    if (ProjectRootsUtil.isModuleContentRoot(directoryFile, project)) {
      ProjectFileIndex fi = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = fi.getModuleForFile(directoryFile);

      data.setPresentableText(directoryFile.getName());
      if (module != null) {
        if (!(parentValue instanceof Module )) {
          if (Comparing.equal(module.getName(), directoryFile.getName())) {
            data.addText(directoryFile.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else {
            data.addText(directoryFile.getName() + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            data.addText("[" + module.getName() + "]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
        }
        else {
          data.addText(directoryFile.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        if (parentValue instanceof Project || parentValue instanceof Module) {
          if (parentValue instanceof Project) {
            data.addText(" (" + ProjectUtil.getLocationRelativeToUserHome(directoryFile.getPresentableUrl()) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          } else {
            data.addText(" (" + directoryFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
        else if (ProjectRootsUtil.isSourceOrTestRoot(directoryFile, project)) {
          if (ProjectRootsUtil.isInTestSource(directoryFile, project)) {
            data.addText(" (test source root)", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
          else {
            data.addText(" (source root)",  SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }

        setupIcon(data, psiDirectory);

        return;
      }
    }

    final String name = parentValue instanceof Project
                        ? psiDirectory.getVirtualFile().getPresentableUrl()
                        : ProjectViewDirectoryHelper.getInstance(psiDirectory.getProject()).getNodeName(getSettings(), parentValue, psiDirectory);
    if (name == null) {
      setValue(null);
      return;
    }

    data.setPresentableText(name);
    if (ProjectRootsUtil.isLibraryRoot(directoryFile, project)) {
      data.setLocationString("library home");
    }
    else {
      data.setLocationString(ProjectViewDirectoryHelper.getInstance(project).getLocationString(psiDirectory));
    }

    setupIcon(data, psiDirectory);
  }

  private void setupIcon(PresentationData data, PsiDirectory psiDirectory) {
    final VirtualFile virtualFile = psiDirectory.getVirtualFile();
    if (PlatformUtils.isCidr()) {
      final Icon openIcon = IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_OPEN, myProject);
      if (openIcon != null) {
        final Icon closedIcon = IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_CLOSED, myProject);
        if (closedIcon != null) {
          data.setOpenIcon(patchIcon(openIcon, virtualFile));
          data.setClosedIcon(patchIcon(closedIcon, virtualFile));
        }
      }
    }
    else {
      for (final IconProvider provider : Extensions.getExtensions(IconProvider.EXTENSION_POINT_NAME)) {
        final Icon openIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_OPEN);
        if (openIcon != null) {
          final Icon closedIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_CLOSED);
          if (closedIcon != null) {
            data.setOpenIcon(patchIcon(openIcon, virtualFile));
            data.setClosedIcon(patchIcon(closedIcon, virtualFile));
            return;
          }
        }
      }
    }
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    return ProjectViewDirectoryHelper.getInstance(myProject).getDirectoryChildren(getValue(), getSettings(), true);
  }

  public String getTestPresentation() {
    return "PsiDirectory: " + getValue().getName();
  }

  public boolean isFQNameShown() {
    return ProjectViewDirectoryHelper.getInstance(getProject()).isShowFQName(getSettings(), getParentValue(), getValue());
  }

  public boolean contains(@NotNull VirtualFile file) {
    final PsiDirectory value = getValue();
    if (value == null) {
      return false;
    }

    VirtualFile directory = value.getVirtualFile();
    if (directory.getFileSystem() instanceof LocalFileSystem) {
      file = PathUtil.getLocalFile(file);
    }

    if (!VfsUtil.isAncestor(directory, file, false)) {
      return false;
    }

    final Project project = value.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return !fileIndex.isIgnored(file);
  }

  public VirtualFile getVirtualFile() {
    PsiDirectory directory = getValue();
    if (directory == null) return null;
    return directory.getVirtualFile();
  }

  public boolean canRepresent(final Object element) {
    if (super.canRepresent(element)) return true;
    PsiDirectory directory = getValue();
    if (directory == null) return false;
    return ProjectViewDirectoryHelper.getInstance(getProject()).canRepresent(element, directory);
  }

  public boolean canNavigate() {
    VirtualFile file = getVirtualFile();
    Project project = getProject();

    ProjectSettingsService service = ProjectSettingsService.getInstance(myProject);
    return file != null && ((ProjectRootsUtil.isModuleContentRoot(file, project) && service.canOpenModuleSettings()) ||
                            (ProjectRootsUtil.isSourceOrTestRoot(file, project)  && service.canOpenContentEntriesSettings()) ||
                            (ProjectRootsUtil.isLibraryRoot(file, project) && service.canOpenModuleLibrarySettings()));
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(final boolean requestFocus) {
    Module module = ModuleUtil.findModuleForPsiElement(getValue());
    if (module != null) {
      final VirtualFile file = getVirtualFile();
      final Project project = getProject();
      ProjectSettingsService service = ProjectSettingsService.getInstance(myProject);
      if (ProjectRootsUtil.isModuleContentRoot(file, project)) {
        service.openModuleSettings(module);
      }
      else if (ProjectRootsUtil.isLibraryRoot(file, project)) {
        service.openModuleLibrarySettings(module);
      }
      else {
        service.openContentEntriesSettings(module);
      }
    }
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    VirtualFile file = getVirtualFile();
    Project project = getProject();

    if (file != null) {
      if (ProjectRootsUtil.isModuleContentRoot(file, project) ||
          ProjectRootsUtil.isSourceOrTestRoot(file, project)) {
        return "Open Module Settings";
      }
      if (ProjectRootsUtil.isLibraryRoot(file, project)) {
        return "Open Library Settings";
      }
    }

    return null;
  }

  public int getWeight() {
    return isFQNameShown() ? 70 : 0;
  }

  public String getTitle() {
    final PsiDirectory directory = getValue();
    if (directory != null) {
      return PsiDirectoryFactory.getInstance(getProject()).getQualifiedName(directory, true);
    }
    return super.getTitle();
  }

  private Icon patchIcon(Icon original, VirtualFile file) {
    Bookmark bookmarkAtFile = BookmarkManager.getInstance(myProject).findFileBookmark(file);
    if (bookmarkAtFile != null) {
      RowIcon composite = new RowIcon(2);
      composite.setIcon(original, 0);
      composite.setIcon(bookmarkAtFile.getIcon(), 1);
      return addReadMark(composite, file.isWritable());
    }

    return addReadMark(original, file.isWritable());
  }

  private static Icon addReadMark(Icon originalIcon, boolean isWritable) {
    if (isWritable) {
      return originalIcon;
    }
    else {
      return LayeredIcon.create(originalIcon, Icons.LOCKED_ICON);
    }
  }

  public String getQualifiedNameSortKey() {
    final PsiDirectoryFactory factory = PsiDirectoryFactory.getInstance(getProject());
    return factory.getQualifiedName(getValue(), true);
  }

  public int getTypeSortWeight(final boolean sortByType) {
    return 3;
  }

  @Override
  public boolean shouldDrillDownOnEmptyElement() {
    return true;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
