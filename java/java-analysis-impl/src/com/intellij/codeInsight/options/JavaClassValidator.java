// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.ui.StringValidatorWithSwingSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A validator that accepts Java class names and provides a chooser UI
 */
public class JavaClassValidator implements StringValidatorWithSwingSelector {
  private final List<String> mySuperClasses;
  private final boolean myAnnotationOnly;
  private final @NlsContexts.DialogTitle String myTitle;

  /**
   * Create default validator that accepts any class names
   */
  public JavaClassValidator() {
    this(List.of(), false, null);
  }

  private JavaClassValidator(@NotNull List<String> superClasses, boolean annotationOnly, @Nullable @NlsContexts.DialogTitle String title) {
    mySuperClasses = superClasses;
    myAnnotationOnly = annotationOnly;
    myTitle = title;
  }

  /**
   * @param superClasses allowed superclasses
   * @return a new validator that accepts classes having one of the specified superclass only
   */
  public @NotNull JavaClassValidator withSuperClass(@NotNull String @NotNull ... superClasses) {
    return new JavaClassValidator(List.of(superClasses), myAnnotationOnly, myTitle);
  }

  /**
   * @param title chooser title
   * @return a new validator whose chooser UI has a specified title
   */
  public @NotNull JavaClassValidator withTitle(@NotNull @NlsContexts.DialogTitle String title) {
    return new JavaClassValidator(mySuperClasses, myAnnotationOnly, title);
  }

  /**
   * @return a new validator that is limited to annotation classes only
   */
  public @NotNull JavaClassValidator annotationsOnly() {
    return new JavaClassValidator(mySuperClasses, true, myTitle);
  }

  @Override
  public @NotNull String validatorId() {
    return "jvm.class";
  }

  @Override
  public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String className) {
    if (!PsiNameHelperImpl.getInstance().isQualifiedName(className)) {
      return JavaBundle.message("validator.text.not.valid.class.name");
    }
    if (project == null) return null;
    //wait for loading
    if (project.isDefault() || DumbService.isDumb(project)) {
      return null;
    }
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
    if (psiClass == null) {
      return JavaBundle.message("validator.text.class.not.found");
    }
    return checkClass(psiClass);
  }

  @Nullable
  private @Nls String checkClass(PsiClass psiClass) {
    if (myAnnotationOnly && !psiClass.isAnnotationType()) {
      return JavaBundle.message("validator.text.no.annotation");
    }
    if (!mySuperClasses.isEmpty() &&
        !ContainerUtil.exists(mySuperClasses, superClass -> InheritanceUtil.isInheritor(psiClass, superClass))) {
      return JavaBundle.message("validator.text.wrong.superclass");
    }
    return null;
  }

  @Override
  public @Nullable String select(@NotNull Project project) {
    //wait for loading
    if (project.isDefault() || DumbService.isDumb(project)) {
      return null;
    }

    String title = myTitle != null ? myTitle :
                   myAnnotationOnly ?
                   JavaBundle.message("special.annotations.list.annotation.class") :
                   JavaBundle.message("dialog.title.choose.class");
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createWithInnerClassesScopeChooser(title, GlobalSearchScope.allScope(project), new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return checkClass(aClass) == null;
        }
      }, null);
    chooser.showDialog();
    final PsiClass selected = chooser.getSelected();
    return selected == null ? null : selected.getQualifiedName();
  }
}
