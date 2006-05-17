/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public abstract class TreeClassChooserFactory implements ProjectComponent{
  public static TreeClassChooserFactory getInstance(Project project) {
    return project.getComponent(TreeClassChooserFactory.class);
  }

  public abstract @NotNull TreeClassChooser createWithInnerClassesScopeChooser(String title, GlobalSearchScope scope, final TreeClassChooser.ClassFilter classFilter, PsiClass initialClass);

  public abstract @NotNull TreeClassChooser createNoInnerClassesScopeChooser(String title, GlobalSearchScope scope, TreeClassChooser.ClassFilter classFilter, PsiClass initialClass);

  public abstract @NotNull TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass);

  public abstract @NotNull TreeClassChooser createProjectScopeChooser(String title);

  public abstract @NotNull TreeClassChooser createAllProjectScopeChooser(String title);

  public abstract @NotNull TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, boolean acceptsSelf, boolean acceptInner, Condition<? super PsiClass> additionalCondition);

  public abstract @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter);

  public abstract @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter, boolean disableStructureProviders);
}
