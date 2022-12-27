// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.ui.StringValidatorWithSwingSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClassValidator implements StringValidatorWithSwingSelector {
  private final String mySuperClass;
  private final boolean myAnnotationOnly;

  public JavaClassValidator(@Nullable String superClass, boolean annotationOnly) {
    mySuperClass = superClass;
    myAnnotationOnly = annotationOnly;
  }

  @Override
  public @NotNull String validatorId() {
    return "jvm.class";
  }

  @Override
  public @Nullable String getErrorMessage(@NotNull String className) {
    return PsiNameHelperImpl.getInstance().isQualifiedName(className) ? null:
           JavaBundle.message("validator.text.not.valid.class.name");
  }

  @Override
  public @Nullable String select(@NotNull Project project) {
    String title = myAnnotationOnly ?
                   JavaBundle.message("special.annotations.list.annotation.class") :
                   JavaBundle.message("dialog.title.choose.class");
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createWithInnerClassesScopeChooser(title, GlobalSearchScope.allScope(project), new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          if (myAnnotationOnly && !aClass.isAnnotationType()) return false;
          if (mySuperClass != null && !InheritanceUtil.isInheritor(aClass, mySuperClass)) return false;
          return true;
        }
      }, null);
    chooser.showDialog();
    final PsiClass selected = chooser.getSelected();
    return selected == null ? null : selected.getQualifiedName();
  }
}
