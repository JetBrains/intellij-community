// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public abstract class TreeClassChooserFactory {

  public static TreeClassChooserFactory getInstance(Project project) {
    return project.getService(TreeClassChooserFactory.class);
  }


  @NotNull
  public abstract TreeClassChooser createWithInnerClassesScopeChooser(@NlsContexts.DialogTitle String title,
                                                                      GlobalSearchScope scope,
                                                                      final ClassFilter classFilter,
                                                                      @Nullable PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createNoInnerClassesScopeChooser(@NlsContexts.DialogTitle String title,
                                                                    GlobalSearchScope scope,
                                                                    ClassFilter classFilter,
                                                                    @Nullable PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createProjectScopeChooser(@NlsContexts.DialogTitle String title, @Nullable PsiClass initialClass);


  @NotNull
  public abstract TreeClassChooser createProjectScopeChooser(@NlsContexts.DialogTitle String title);


  @NotNull
  public abstract TreeClassChooser createAllProjectScopeChooser(@NlsContexts.DialogTitle String title);


  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 boolean acceptsSelf,
                                                                 boolean acceptInner,
                                                                 @Nullable
                                                                 Condition<? super PsiClass> additionalCondition);

  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 PsiClass initialClass);

  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 PsiClass initialClass,
                                                                 ClassFilter classFilter);

  @NotNull
  public abstract TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 PsiClass initialClass,
                                                                 ClassFilter classFilter,
                                                                 @Nullable Comparator<? super NodeDescriptor<?>> comparator);

  /**
   * @deprecated Use {@link TreeFileChooserFactory#createFileChooser(String, PsiFile, FileType, TreeFileChooser.PsiFileFilter)}
   */
  @Deprecated
  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter);


  /**
   * @deprecated Use {@link TreeFileChooserFactory#createFileChooser(String, PsiFile, FileType, TreeFileChooser.PsiFileFilter, boolean)}
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders);

  /**
   * @deprecated Use {@link TreeFileChooserFactory#createFileChooser(String, PsiFile, FileType, TreeFileChooser.PsiFileFilter, boolean, boolean)}
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents);
}
