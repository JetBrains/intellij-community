package com.intellij.ide.util;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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

  public abstract @NotNull TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, boolean acceptsSelf, boolean acceptInner, Condition<PsiClass> addtionalCondition);

  public abstract @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter);
}
