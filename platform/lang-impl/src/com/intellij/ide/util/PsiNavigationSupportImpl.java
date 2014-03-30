/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiNavigationSupportImpl extends PsiNavigationSupport {
  @Nullable
  @Override
  public Navigatable getDescriptor(PsiElement element) {
    return EditSourceUtil.getDescriptor(element);
  }

  @Nullable
  @Override
  public Navigatable createNavigatable(Project project, VirtualFile vFile, int offset) {
    return new OpenFileDescriptor(project, vFile, offset);
  }

  @Override
  public boolean canNavigate(PsiElement element) {
    return EditSourceUtil.canNavigate(element);
  }

  @Override
  public void navigateToDirectory(PsiDirectory psiDirectory, boolean requestFocus) {
    ProjectViewSelectInTarget.select(psiDirectory.getProject(), this, ProjectViewPane.ID, null, psiDirectory.getVirtualFile(), requestFocus);
  }
}
