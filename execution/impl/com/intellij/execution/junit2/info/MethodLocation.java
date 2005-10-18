package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import java.util.Iterator;

// Author: dyoma

public class MethodLocation<E extends PsiElement> extends Location<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.info.MethodLocation");
  private final Project myProject;
  private final E myMethod;
  private final Location<PsiClass> myClassLocation;

  public MethodLocation(final Project project, final E method, final Location<PsiClass> classLocation) {
    LOG.assertTrue(method != null);
    LOG.assertTrue(classLocation != null);
    LOG.assertTrue(project != null);
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static <T extends PsiElement> MethodLocation<T> elementInClass(final T psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation<T>(classLocation.getProject(), psiElement, classLocation);
  }

  public E getPsiElement() {
    return myMethod;
  }

  public Project getProject() {
    return myProject;
  }

  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  public <Ancestor extends PsiElement> Iterator<Location<? extends Ancestor>> getAncestors(final Class<Ancestor> ancestorClass,
                                                                                 final boolean strict) {
    final Iterator<Location<? extends Ancestor>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    final Location<Ancestor> thisLocation = (Location<Ancestor>)(Location)this;
    return new Iterator<Location<? extends Ancestor>>() {
      private boolean myFirstStep = ancestorClass.isInstance(myMethod);
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      public Location<? extends Ancestor> next() {
        final Location<? extends Ancestor> location;
        if (myFirstStep) {location = thisLocation;}
        else {location = fromClass.next();}
        myFirstStep = false;
        return location;
      }

      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }
}
