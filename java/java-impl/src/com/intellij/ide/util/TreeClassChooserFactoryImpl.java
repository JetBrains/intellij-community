package com.intellij.ide.util;

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
public class TreeClassChooserFactoryImpl extends TreeClassChooserFactory {
  private final Project myProject;

  public TreeClassChooserFactoryImpl(final Project project) {
    myProject = project;
  }

  @NotNull
  public TreeClassChooser createWithInnerClassesScopeChooser(String title,
                                                             GlobalSearchScope scope,
                                                             final TreeClassChooser.ClassFilter classFilter,
                                                             PsiClass initialClass) {
    return TreeClassChooserDialog.withInnerClasses(title, myProject, scope, classFilter, initialClass);
  }

  @NotNull
  public TreeClassChooser createNoInnerClassesScopeChooser(String title,
                                                           GlobalSearchScope scope,
                                                           TreeClassChooser.ClassFilter classFilter,
                                                           PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, scope, classFilter, initialClass);
  }

  @NotNull
  public TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, initialClass);
  }

  @NotNull
  public TreeClassChooser createProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject);
  }

  @NotNull
  public TreeClassChooser createAllProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject, GlobalSearchScope.allScope(myProject), null, null);
  }

  @NotNull
  public TreeClassChooser createInheritanceClassChooser(String title,
                                                        GlobalSearchScope scope,
                                                        PsiClass base,
                                                        boolean acceptsSelf,
                                                        boolean acceptInner,
                                                        Condition<? super PsiClass> additionalCondition) {
    TreeClassChooser.ClassFilter classFilter = new TreeClassChooserDialog.InheritanceClassFilterImpl(base, acceptsSelf, acceptInner, additionalCondition);
    return new TreeClassChooserDialog(title, myProject, scope, classFilter, base, null, null);
  }

  @NotNull
  public TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass) {
    return createInheritanceClassChooser(title, scope, base, initialClass, null);
  }

  @NotNull
  public TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass,
                                                        TreeClassChooser.ClassFilter classFilter) {
    return new TreeClassChooserDialog(title, myProject, scope, classFilter, base, initialClass, null);
  }

  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title,
                                           final PsiFile initialFile,
                                           FileType fileType,
                                           TreeFileChooser.PsiFileFilter filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, false, false);
  }

  @NotNull
  public
  TreeFileChooser createFileChooser(@NotNull String title,
                                    @Nullable PsiFile initialFile,
                                    @Nullable FileType fileType,
                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                    boolean disableStructureProviders) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, false);
  }


  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType,
                                           @Nullable TreeFileChooser.PsiFileFilter filter,
                                           boolean disableStructureProviders,
                                           boolean showLibraryContents) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, showLibraryContents);
  }
}
