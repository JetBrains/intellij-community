// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class TreeClassChooserFactoryImpl extends TreeClassChooserFactory {
  private final @NotNull Project myProject;

  public TreeClassChooserFactoryImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull TreeClassChooser createWithInnerClassesScopeChooser(@NlsContexts.DialogTitle String title,
                                                                      GlobalSearchScope scope,
                                                                      final ClassFilter classFilter,
                                                                      PsiClass initialClass) {
    return TreeJavaClassChooserDialog.withInnerClasses(title, myProject, scope, classFilter, initialClass);
  }

  @Override
  public @NotNull TreeClassChooser createNoInnerClassesScopeChooser(@NlsContexts.DialogTitle String title,
                                                                    GlobalSearchScope scope,
                                                                    ClassFilter classFilter,
                                                                    PsiClass initialClass) {
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, initialClass);
  }

  @Override
  public @NotNull TreeClassChooser createProjectScopeChooser(@NlsContexts.DialogTitle String title, PsiClass initialClass) {
    return new TreeJavaClassChooserDialog(title, myProject, initialClass);
  }

  @Override
  public @NotNull TreeClassChooser createProjectScopeChooser(@NlsContexts.DialogTitle String title) {
    return new TreeJavaClassChooserDialog(title, myProject);
  }

  @Override
  public @NotNull TreeClassChooser createAllProjectScopeChooser(@NlsContexts.DialogTitle String title) {
    return new TreeJavaClassChooserDialog(title, myProject, GlobalSearchScope.allScope(myProject), null, null);
  }

  @Override
  public @NotNull TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 boolean acceptsSelf,
                                                                 boolean acceptInner,
                                                                 @Nullable
                                                        Condition<? super PsiClass> additionalCondition) {
    ClassFilter classFilter = new TreeJavaClassChooserDialog.InheritanceJavaClassFilterImpl(base, acceptsSelf, acceptInner, additionalCondition);
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, base, null, false);
  }

  @Override
  public @NotNull TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass) {
    return createInheritanceClassChooser(title, scope, base, initialClass, null);
  }

  @Override
  public @NotNull TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass,
                                                                 ClassFilter classFilter) {
    return createInheritanceClassChooser(title, scope, base, initialClass, classFilter, null);
  }

  @Override
  public @NotNull TreeClassChooser createInheritanceClassChooser(@NlsContexts.DialogTitle String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass,
                                                                 ClassFilter classFilter, @Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, comparator, base, initialClass, false);
  }

  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                                    final PsiFile initialFile,
                                                    FileType fileType,
                                                    TreeFileChooser.PsiFileFilter filter) {
    return createFileChooser(title, initialFile, fileType, filter, false);
  }

  @Override
  public @NotNull
  TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                    @Nullable PsiFile initialFile,
                                    @Nullable FileType fileType,
                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                    boolean disableStructureProviders) {
    return createFileChooser(title, initialFile, fileType, filter, disableStructureProviders, false);
  }


  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull @NlsContexts.DialogTitle String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents) {
    return TreeFileChooserFactory.getInstance(myProject)
      .createFileChooser(title, initialFile, fileType, filter, disableStructureProviders, showLibraryContents);
  }
}
