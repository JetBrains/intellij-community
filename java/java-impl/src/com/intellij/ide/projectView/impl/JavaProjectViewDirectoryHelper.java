// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.util.FontUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author anna
 */
public final class JavaProjectViewDirectoryHelper extends ProjectViewDirectoryHelper {
  public JavaProjectViewDirectoryHelper(Project project) {
    super(project);
  }

  @Nullable
  @Override
  public String getLocationString(@NotNull PsiDirectory directory, boolean includeUrl, boolean includeRootType) {
    String result = null;
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (ProjectRootsUtil.isSourceRoot(directory) && aPackage != null) {   //package prefix
      result = StringUtil.nullize(aPackage.getQualifiedName(), true);
    }
    String baseString = super.getLocationString(directory, includeUrl, includeRootType);
    if (result == null) return baseString;
    if (baseString == null) return result;
    return result  + "," + FontUtil.spaceAndThinSpace() + baseString;
  }

  @Override
  public boolean isShowFQName(final ViewSettings settings, final Object parentValue, final PsiDirectory value) {
    PsiPackage aPackage;
    return value != null &&
           !(parentValue instanceof Project) &&
           settings.isFlattenPackages() &&
           (aPackage = JavaDirectoryService.getInstance().getPackage(value)) != null &&
           !aPackage.getQualifiedName().isEmpty();
  }

  @Override
  public boolean shouldHideProjectConfigurationFilesDirectory() {
    return false;
  }

  @NotNull
  @Override
  public String getNodeName(final ViewSettings settings, final Object parentValue, final PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);

    PsiPackage parentPackage;
    if (!ProjectRootsUtil.isSourceRoot(directory) && aPackage != null && !aPackage.getQualifiedName().isEmpty() && parentValue instanceof PsiDirectory) {
      parentPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)parentValue));
    }
    else if (ProjectRootsUtil.isSourceRoot(directory) && aPackage != null) {   //package prefix
      aPackage = null;
      parentPackage = null;
    }
    else {
      parentPackage = null;
    }

    return PackageUtil.getNodeName(settings, aPackage, parentPackage, directory.getName(), isShowFQName(settings, parentValue, directory));
  }

  @Override
  public boolean skipDirectory(final PsiDirectory directory) {
    return JavaDirectoryService.getInstance().getPackage(directory) == null;
  }

  @Override
  public boolean isEmptyMiddleDirectory(final PsiDirectory directory,
                                        final boolean strictlyEmpty,
                                        @Nullable PsiFileSystemItemFilter filter) {
    return JavaDirectoryService.getInstance().getPackage(directory) != null &&
           TreeViewUtil.isEmptyMiddlePackage(directory, strictlyEmpty, filter);
  }

  @Override
  public boolean supportsFlattenPackages() {
    return true;
  }

  @Override
  public boolean supportsHideEmptyMiddlePackages() {
    return true;
  }

  @Override
  public boolean canRepresent(final Object element, final PsiDirectory directory) {
    if (super.canRepresent(element, directory)) return true;
    if (element instanceof PackageElement) {
      final PackageElement packageElement = (PackageElement)element;
      return Arrays.asList(packageElement.getPackage().getDirectories()).contains(directory);
    }
    return false;
  }
}
