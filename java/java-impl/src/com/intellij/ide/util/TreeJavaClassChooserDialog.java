// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

public class TreeJavaClassChooserDialog extends AbstractTreeClassChooserDialog<PsiClass> implements TreeClassChooser {
  public TreeJavaClassChooserDialog(String title, Project project) {
    super(title, project, PsiClass.class);
  }

  public TreeJavaClassChooserDialog(String title, Project project, @Nullable PsiClass initialClass) {
    super(title, project, PsiClass.class, initialClass);
  }

  public TreeJavaClassChooserDialog(String title,
                                    @NotNull Project project,
                                    GlobalSearchScope scope,
                                    final ClassFilter classFilter, @Nullable PsiClass initialClass) {
    super(title, project, scope, PsiClass.class, createFilter(classFilter), initialClass);
  }


  public TreeJavaClassChooserDialog(String title,
                                    @NotNull Project project,
                                    GlobalSearchScope scope,
                                    @Nullable ClassFilter classFilter,
                                    PsiClass baseClass,
                                    @Nullable PsiClass initialClass, boolean isShowMembers) {
    super(title, project, scope, PsiClass.class, createFilter(classFilter), baseClass, initialClass, isShowMembers, true);
  }

  public static TreeJavaClassChooserDialog withInnerClasses(String title,
                                                            @NotNull Project project,
                                                            GlobalSearchScope scope,
                                                            final ClassFilter classFilter,
                                                            @Nullable PsiClass initialClass) {
    return new TreeJavaClassChooserDialog(title, project, scope, classFilter, null, initialClass, true);
  }

  @Nullable
  private static Filter<PsiClass> createFilter(@Nullable final ClassFilter classFilter) {
    if (classFilter == null) {
      return null;
    }
    else {
      return new Filter<PsiClass>() {
        @Override
        public boolean isAccepted(final PsiClass element) {
          return ReadAction.compute(() -> classFilter.isAccepted(element));
        }
      };
    }
  }

  @Override
  @Nullable
  protected PsiClass getSelectedFromTreeUserObject(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ClassTreeNode)) return null;
    ClassTreeNode descriptor = (ClassTreeNode)userObject;
    return descriptor.getPsiClass();
  }

  @Override
  @NotNull
  protected List<PsiClass> getClassesByName(final String name,
                                            final boolean checkBoxState,
                                            final String pattern,
                                            final GlobalSearchScope searchScope) {
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(getProject());
    PsiClass[] classes =
      cache.getClassesByName(name, checkBoxState ? searchScope : GlobalSearchScope.projectScope(getProject()).intersectWith(searchScope));
    return ContainerUtil.newArrayList(classes);
  }

  @NotNull
  @Override
  protected BaseClassInheritorsProvider<PsiClass> getInheritorsProvider(@NotNull PsiClass baseClass) {
    return new JavaInheritorsProvider(getProject(), baseClass, getScope());
  }

  private static class JavaInheritorsProvider extends BaseClassInheritorsProvider<PsiClass> {
    private final Project myProject;

    JavaInheritorsProvider(Project project, PsiClass baseClass, GlobalSearchScope scope) {
      super(baseClass, scope);
      myProject = project;
    }

    @NotNull
    @Override
    protected Query<PsiClass> searchForInheritors(PsiClass baseClass, GlobalSearchScope searchScope, boolean checkDeep) {
      return ClassInheritorsSearch.search(baseClass, searchScope, checkDeep);
    }

    @Override
    protected boolean isInheritor(PsiClass clazz, PsiClass baseClass, boolean checkDeep) {
      return clazz.isInheritor(baseClass, checkDeep);
    }

    @Override
    protected String[] getNames() {
      return PsiShortNamesCache.getInstance(myProject).getAllClassNames();
    }
  }

  public static class InheritanceJavaClassFilterImpl implements ClassFilter {
    private final PsiClass myBase;
    private final boolean myAcceptsSelf;
    private final boolean myAcceptsInner;
    @NotNull
    private final Condition<? super PsiClass> myAdditionalCondition;

    public InheritanceJavaClassFilterImpl(PsiClass base,
                                          boolean acceptsSelf,
                                          boolean acceptInner,
                                          @Nullable
                                          Condition<? super PsiClass> additionalCondition) {
      myAcceptsSelf = acceptsSelf;
      myAcceptsInner = acceptInner;
      if (additionalCondition == null) {
        additionalCondition = Conditions.alwaysTrue();
      }
      myAdditionalCondition = additionalCondition;
      myBase = base;
    }

    @Override
    public boolean isAccepted(PsiClass aClass) {
      if (!myAcceptsInner && !(aClass.getParent() instanceof PsiJavaFile)) return false;
      if (!myAdditionalCondition.value(aClass)) return false;
      // we've already checked for inheritance
      return myAcceptsSelf || !aClass.getManager().areElementsEquivalent(aClass, myBase);
    }
  }
}
