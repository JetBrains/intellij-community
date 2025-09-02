// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PackageElementNode extends ProjectViewNode<PackageElement> implements ValidateableNode {
  public PackageElementNode(@NotNull Project project, @NotNull PackageElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    if (!isUnderContent(file) || getValue() == null) {
      return false;
    }

    final PsiDirectory[] directories = getValue().getPackage().getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtilCore.isAncestor(directory.getVirtualFile(), file, false)) return true;
    }
    return false;
  }

  private boolean isUnderContent(@NotNull VirtualFile file) {
    PackageElement element = getValue();
    Module module = element == null ? null : element.getModule();
    if (module == null) {
      return ModuleUtilCore.projectContainsFile(getProject(), file, isLibraryElement());
    }
    return ModuleUtilCore.moduleContainsFile(module, file, isLibraryElement());
  }

  private boolean isLibraryElement() {
    return getValue() != null && getValue().isLibraryElement();
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    final PackageElement value = getValue();
    if (value == null) return Collections.emptyList();
    final List<AbstractTreeNode<?>> children = new ArrayList<>();
    final Module module = value.getModule();
    final PsiPackage aPackage = value.getPackage();
    var nodeBuilder = new PackageNodeBuilder(module, isLibraryElement());

    if (!getSettings().isFlattenPackages()) {

      final PsiPackage[] subpackages = nodeBuilder.getSubpackages(aPackage);
      for (PsiPackage subpackage : subpackages) {
        nodeBuilder.addPackageAsChild(children, subpackage, getSettings());
      }
    }
    // process only files in package's directories
    final GlobalSearchScope scopeToShow = PackageUtil.getScopeToShow(aPackage.getProject(), module, isLibraryElement());
    PsiFile[] packageChildren = aPackage.getFiles(scopeToShow);
    for (PsiFile file : packageChildren) {
      if (file.getVirtualFile() != null) {
        children.add(new PsiFileNode(getProject(), file, getSettings()));
      }
    }
    return children;
  }

  @Override
  public boolean validate() {
    return super.validate() && isValid();
  }

  @Override
  public boolean isValid() {
    PackageElement value = getValue();
    if (value != null && value.getPackage().isValid()) {
      Module module = value.getModule();
      return module == null || !module.isDisposed();
    }
    return false;
  }

  @Override
  protected void update(final @NotNull PresentationData presentation) {
    try {
      if (isValid()) {
        updateValidData(presentation, getValue());
        return;
      }
    }
    catch (IndexNotReadyException ignore) {}
    setValue(null);
  }

  private void updateValidData(PresentationData presentation, PackageElement value) {
    final PsiPackage aPackage = value.getPackage();

    if (!getSettings().isFlattenPackages()
        && getSettings().isHideEmptyMiddlePackages()
        && PackageUtil.isPackageEmpty(aPackage, value.getModule(), true, isLibraryElement())) {
      setValue(null);
      return;
    }

    PsiPackage parentPackage = getParentPackage();
    String qName = aPackage.getQualifiedName();
    String name = PackageUtil.getNodeName(getSettings(), aPackage,parentPackage, qName, showFQName(aPackage));
    presentation.setPresentableText(name);

    presentation.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));

    if (myProject != null) CompoundProjectViewNodeDecorator.get(myProject).decorate(this, presentation);
  }

  private boolean showFQName(final PsiPackage aPackage) {
    return getSettings().isFlattenPackages() && !aPackage.getQualifiedName().isEmpty();
  }

  @Override
  public String getTestPresentation() {
    final PresentationData presentation = new PresentationData();
    update(presentation);
    return "PsiPackage: " + presentation.getPresentableText();
  }

  @Override
  public boolean valueIsCut() {
    return getValue() != null && CopyPasteManager.getInstance().isCutElement(getValue().getPackage());
  }

  public VirtualFile @NotNull [] getVirtualFiles() {
    final PackageElement value = getValue();
    if (value == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final PsiDirectory[] directories = PackageUtil.getDirectories(value.getPackage(), value.getModule(), isLibraryElement());
    final VirtualFile[] result = new VirtualFile[directories.length];
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      result[i] = directory.getVirtualFile();
    }
    return result;
  }

  @Override
  public boolean canRepresent(final Object element) {
    if (super.canRepresent(element)) return true;
    PackageElement value = getValue();
    if (value == null) return true;
    if (element instanceof PackageElement packageElement) {
      String otherPackage = packageElement.getPackage().getQualifiedName();
      String aPackage = value.getPackage().getQualifiedName();
      if (otherPackage.equals(aPackage)) {
        return true;
      }
    }
    if (element instanceof PsiDirectory directory) {
      return isPackageUnderDirectory(value, directory.getVirtualFile());
    }
    if (element instanceof VirtualFile file) {
      return file.isDirectory() && isPackageUnderDirectory(value, file);
    }
    return false;
  }

  private boolean isPackageUnderDirectory(@NotNull PackageElement element, @NotNull VirtualFile file) {
    PsiPackage parent = getParentPackage();
    for (PsiPackage p = element.getPackage(); p != null && !p.equals(parent); p = p.getParentPackage()) {
      for (PsiDirectory directory : p.getDirectories()) {
        if (directory.getVirtualFile().equals(file)) return true;
      }
    }
    return false;
  }

  private PsiPackage getParentPackage() {
    Object value = getParentValue();
    return value instanceof PackageElement ? ((PackageElement)value).getPackage() : null;
  }

  @Override
  public int getWeight() {
    return 0;
  }

  @Override
  public String getTitle() {
    PackageElement packageElement = getValue();
    if (packageElement == null) {
      return super.getTitle();
    }
    return packageElement.getPackage().getQualifiedName();
  }

  @Override
  public @Nullable String getQualifiedNameSortKey() {
    PackageElement packageElement = getValue();
    if (packageElement != null) {
      return packageElement.getPackage().getQualifiedName();
    }
    return null;
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    return 4;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    for (final VirtualFile dir : getVirtualFiles()) {
      if (dir.getChildren().length > 0) {
        return true;
      }
    }
    return false;
  }
}
