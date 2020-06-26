// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public final class JavaAwareFilter {
  private JavaAwareFilter() { }

  public static Filter METHOD(@NotNull final Project project, @NotNull final GlobalSearchScope searchScope) {
    return new Filter() {
      @Override
      public boolean shouldAccept(final AbstractTestProxy test) {
        Location location = test.getLocation(project, searchScope);
        return location instanceof MethodLocation ||
               location instanceof PsiLocation && location.getPsiElement() instanceof PsiMethod;
      }
    };
  }
}