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
public abstract class TreeClassChooserFactory implements ProjectComponent {
  public static TreeClassChooserFactory getInstance(Project project) {
    return project.getComponent(TreeClassChooserFactory.class);
  }


  @NotNull
  public abstract TreeClassChooser createWithInnerClassesScopeChooser(String title,
                                                                      GlobalSearchScope scope,
                                                                      final TreeClassChooser.ClassFilter classFilter,
                                                                      PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createNoInnerClassesScopeChooser(String title,
                                                                    GlobalSearchScope scope,
                                                                    TreeClassChooser.ClassFilter classFilter,
                                                                    PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createProjectScopeChooser(String title);


  @NotNull
  public abstract TreeClassChooser createAllProjectScopeChooser(String title);


  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 boolean acceptsSelf,
                                                                 boolean acceptInner,
                                                                 Condition<? super PsiClass> additionalCondition);

  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 PsiClass initialClass);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders);
}
