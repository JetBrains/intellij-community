package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

// Author: dyoma

public class MethodLocation extends Location<PsiMethod> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.info.MethodLocation");
  private final Project myProject;
  @NotNull private final PsiMethod myMethod;
  private final Location<PsiClass> myClassLocation;

  public MethodLocation(@NotNull final Project project, @NotNull final PsiMethod method, @NotNull final Location<PsiClass> classLocation) {
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static MethodLocation elementInClass(final PsiMethod psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation(classLocation.getProject(), psiElement, classLocation);
  }

  @NotNull
  public PsiMethod getPsiElement() {
    return myMethod;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  @NotNull
  public <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass, final boolean strict) {
    final Iterator<Location<T>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    return new Iterator<Location<T>>() {
      private boolean myFirstStep = ancestorClass.isInstance(myMethod);
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      public Location<T> next() {
        final Location<T> location = myFirstStep ? (Location<T>)(Location)MethodLocation.this : fromClass.next();
        myFirstStep = false;
        return location;
      }

      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }
}
