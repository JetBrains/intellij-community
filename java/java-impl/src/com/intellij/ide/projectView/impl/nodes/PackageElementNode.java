// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
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
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PackageElementNode extends ProjectViewNode<PackageElement> {
  public PackageElementNode(@NotNull Project project,
                            final PackageElement value,
                            final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PackageElementNode(@NotNull Project project,
                            final Object value,
                            final ViewSettings viewSettings) {
    this(project, (PackageElement)value, viewSettings);
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    if (!isUnderContent(file) || getValue() == null) {
      return false;
    }

    final PsiDirectory[] directories = getValue().getPackage().getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtilCore.isAncestor(directory.getVirtualFile(), file, false)) return true;
    }
    return false;
  }

  private boolean isUnderContent(final VirtualFile file) {
    PackageElement element = getValue();
    final Module module = element == null ? null : element.getModule();
    if (module == null) {
      return ModuleUtilCore.projectContainsFile(getProject(), file, isLibraryElement());
    }
    else {
      return ModuleUtilCore.moduleContainsFile(module, file, isLibraryElement());
    }
  }

  private boolean isLibraryElement() {
    return getValue() != null && getValue().isLibraryElement();
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final PackageElement value = getValue();
    if (value == null) return Collections.emptyList();
    final List<AbstractTreeNode> children = new ArrayList<>();
    final Module module = value.getModule();
    final PsiPackage aPackage = value.getPackage();

    if (!getSettings().isFlattenPackages()) {

      final PsiPackage[] subpackages = PackageUtil.getSubpackages(aPackage, module, isLibraryElement());
      for (PsiPackage subpackage : subpackages) {
        PackageUtil.addPackageAsChild(children, subpackage, module, getSettings(), isLibraryElement());
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
    PackageElement value = getValue();
    return value != null && value.getPackage().isValid() && (value.getModule() == null || !value.getModule().isDisposed());
  }

  @Override
  protected void update(final PresentationData presentation) {
    try {
      if (validate()) {
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

    PsiPackage parentPackage;
    Object parentValue = getParentValue();
    if (parentValue instanceof PackageElement) {
      parentPackage = ((PackageElement)parentValue).getPackage();
    }
    else {
      parentPackage = null;
    }
    String qName = aPackage.getQualifiedName();
    String name = PackageUtil.getNodeName(getSettings(), aPackage,parentPackage, qName, showFQName(aPackage));
    presentation.setPresentableText(name);

    presentation.setIcon(PlatformIcons.PACKAGE_ICON);

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

  @NotNull
  public VirtualFile[] getVirtualFiles() {
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
    final PackageElement value = getValue();
    if (value == null) return true;
    if (element instanceof PackageElement) {
      final PackageElement packageElement = (PackageElement)element;
      final String otherPackage = packageElement.getPackage().getQualifiedName();
      final String aPackage = value.getPackage().getQualifiedName();
      if (otherPackage.equals(aPackage)) {
        return true;
      }
    }
    if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      return Arrays.asList(value.getPackage().getDirectories()).contains(directory);
    }
    return false;
  }

  @Override
  public int getWeight() {
    return 0;
  }

  @Override
  public String getTitle() {
    final PackageElement packageElement = getValue();
    if (packageElement == null) {
      return super.getTitle();
    }
    return packageElement.getPackage().getQualifiedName();
  }

  @Override
  @Nullable
  public String getQualifiedNameSortKey() {
    final PackageElement packageElement = getValue();
    if (packageElement != null) {
      return packageElement.getPackage().getQualifiedName();
    }
    return null;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
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
