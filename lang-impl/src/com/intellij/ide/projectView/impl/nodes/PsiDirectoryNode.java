package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class PsiDirectoryNode extends BasePsiNode<PsiDirectory> {
  public PsiDirectoryNode(Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected void updateImpl(PresentationData data) {
    final Project project = getProject();
    final PsiDirectory psiDirectory = getValue();
    final VirtualFile directoryFile = psiDirectory.getVirtualFile();
    final String name = getParentValue() instanceof Project
                        ? psiDirectory.getVirtualFile().getPresentableUrl()
                        : ProjectViewDirectoryHelper.getInstance(psiDirectory.getProject()).getNodeName(getSettings(), getParentValue(), psiDirectory);
    if (name == null) {
      setValue(null);
      return;
    }

    final VirtualFile virtualFile = psiDirectory.getVirtualFile();
    final boolean isWritable = virtualFile.isWritable();

    data.setPresentableText(name);

    for (final IconProvider provider : ApplicationManager.getApplication().getComponents(IconProvider.class)) {
      final Icon openIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_OPEN);
      if (openIcon != null) {
        final Icon closedIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_CLOSED);
        if (closedIcon != null) {
          data.setOpenIcon(addReadMark(openIcon, isWritable));
          data.setClosedIcon(addReadMark(closedIcon, isWritable));
          return;
        }
      }
    }
    if (ProjectRootsUtil.isModuleContentRoot(directoryFile, project) || ProjectRootsUtil.isLibraryRoot(directoryFile, project)) {
      data.setLocationString(directoryFile.getPresentableUrl());
    }
    else {
      if (!ProjectRootsUtil.isInTestSource(directoryFile, project)) {
        data.setLocationString(ProjectViewDirectoryHelper.getInstance(project).getLocationString(psiDirectory, false));
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

    if (!VfsUtil.isAncestor(value.getVirtualFile(), file, false)) {
      return false;
    }
    final Project project = value.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(value.getVirtualFile());
    if (module == null) {
      return fileIndex.getModuleForFile(file) == null;
    }
    final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    
    return moduleFileIndex.isInContent(file);
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
    VirtualFile virtualFile = getVirtualFile();
    return virtualFile != null && (ProjectRootsUtil.isSourceOrTestRoot(virtualFile, getProject()) || ProjectRootsUtil.isLibraryRoot(virtualFile, getProject()));
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(final boolean requestFocus) {
    Module module = ModuleUtil.findModuleForPsiElement(getValue());
    if (module != null) {
      ProjectSettingsService.getInstance(myProject).openContentEntriesSettings(module);
    }
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
}
