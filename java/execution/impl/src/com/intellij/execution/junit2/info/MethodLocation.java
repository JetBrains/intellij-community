// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

// Author: dyoma

public class MethodLocation extends Location<PsiMethod> {
  private static final Logger LOG = Logger.getInstance(MethodLocation.class);
  private final Project myProject;
  @NotNull private final PsiMethod myMethod;
  private final Location<? extends PsiClass> myClassLocation;

  public MethodLocation(@NotNull final Project project, @NotNull final PsiMethod method, @NotNull final Location<? extends PsiClass> classLocation) {
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static MethodLocation elementInClass(final PsiMethod psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation(classLocation.getProject(), psiElement, classLocation);
  }

  @Override
  @NotNull
  public PsiMethod getPsiElement() {
    return myMethod;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Override
  public Module getModule() {
    return myClassLocation.getModule();
  }

  @NotNull
  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  @NotNull
  public String getContainingClassJVMClassName() {
    if (myClassLocation instanceof NestedClassLocation) {
      return ((NestedClassLocation)myClassLocation).getNestedInConcreteInheritor();
    }
    return Objects.requireNonNull(ClassUtil.getJVMClassName(myClassLocation.getPsiElement()));
  }
  
  @Override
  @NotNull
  public <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass, final boolean strict) {
    final Iterator<Location<T>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    return new Iterator<>() {
      private boolean myFirstStep = ancestorClass.isInstance(myMethod);

      @Override
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      @Override
      public Location<T> next() {
        final Location<T> location = myFirstStep ? (Location<T>)MethodLocation.this : fromClass.next();
        myFirstStep = false;
        return location;
      }

      @Override
      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }
}
