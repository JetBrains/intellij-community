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

/*
 * User: anna
 * Date: 23-Jan-2008
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaProjectViewDirectoryHelper extends ProjectViewDirectoryHelper {
  public JavaProjectViewDirectoryHelper(Project project, DirectoryIndex index) {
    super(project, index);
  }

  @Override
  public String getLocationString(@NotNull final PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (ProjectRootsUtil.isSourceRoot(directory) && aPackage != null) {   //package prefix
      return aPackage.getQualifiedName();
    }
    return super.getLocationString(directory);
  }

  @Override
  public boolean isShowFQName(final ViewSettings settings, final Object parentValue, final PsiDirectory value) {
    PsiPackage aPackage;
        return value != null
               && !(parentValue instanceof Project)
               && settings.isFlattenPackages()
               && (aPackage = JavaDirectoryService.getInstance().getPackage(value)) != null
               && !aPackage.getQualifiedName().isEmpty();

  }

  @Nullable
  @Override
  public String getNodeName(final ViewSettings settings, final Object parentValue, final PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);

    PsiPackage parentPackage;
    if (!ProjectRootsUtil.isSourceRoot(directory) && aPackage != null && !aPackage.getQualifiedName().isEmpty() &&
                              parentValue instanceof PsiDirectory) {

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
  public boolean isEmptyMiddleDirectory(final PsiDirectory directory, final boolean strictlyEmpty) {
    return JavaDirectoryService.getInstance().getPackage(directory) != null && TreeViewUtil.isEmptyMiddlePackage(directory, strictlyEmpty);
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
