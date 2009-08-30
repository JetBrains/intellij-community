/*
 * User: anna
 * Date: 20-Feb-2008
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;

public class JavaAwareFilter {
  private JavaAwareFilter() {
  }

  public static Filter METHOD(final Project project) {
    return new Filter() {
      public boolean shouldAccept(final AbstractTestProxy test) {
        final Location location = test.getLocation(project);
        if (location instanceof MethodLocation) return true;
        if (location instanceof PsiLocation && location.getPsiElement() instanceof PsiMethod) return true;
        return false;
      }
    };
  }
}