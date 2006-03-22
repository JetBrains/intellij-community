package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.Iterator;

// Author: dyoma

public class MethodLocation extends Location<PsiMethod> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.info.MethodLocation");
  private final Project myProject;
  private final PsiMethod myMethod;
  private final Location<PsiClass> myClassLocation;

  public MethodLocation(final Project project, final PsiMethod method, final Location<PsiClass> classLocation) {
    LOG.assertTrue(method != null);
    LOG.assertTrue(classLocation != null);
    LOG.assertTrue(project != null);
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static MethodLocation elementInClass(final PsiMethod psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation(classLocation.getProject(), psiElement, classLocation);
  }

  public PsiMethod getPsiElement() {
    return myMethod;
  }

  public Project getProject() {
    return myProject;
  }

  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  public <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass,
                                                                                 final boolean strict) {
    final Iterator<Location<T>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    final Location<T> thisLocation = (Location<T>)(Location)this;
    return new Iterator<Location<T>>() {
      private boolean myFirstStep = ancestorClass.isInstance(myMethod);
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      public Location<T> next() {
        final Location<T> location = myFirstStep ? thisLocation : fromClass.next();
        myFirstStep = false;
        return location;
      }

      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }
}
