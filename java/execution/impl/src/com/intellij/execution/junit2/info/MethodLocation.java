// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @NotNull PsiMethod myMethod;
  private final Location<? extends PsiClass> myClassLocation;

  public MethodLocation(final @NotNull Project project, final @NotNull PsiMethod method, final @NotNull Location<? extends PsiClass> classLocation) {
    myProject = project;
    myMethod = method;
    myClassLocation = classLocation;
  }

  public static MethodLocation elementInClass(final PsiMethod psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new MethodLocation(classLocation.getProject(), psiElement, classLocation);
  }

  @Override
  public @NotNull PsiMethod getPsiElement() {
    return myMethod;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable Module getModule() {
    return myClassLocation.getModule();
  }

  public @NotNull PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  public @NotNull String getContainingClassJVMClassName() {
    if (myClassLocation instanceof NestedClassLocation) {
      return ((NestedClassLocation)myClassLocation).getNestedInConcreteInheritor();
    }
    return Objects.requireNonNull(ClassUtil.getJVMClassName(myClassLocation.getPsiElement()));
  }
  
  @Override
  public @NotNull <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass, final boolean strict) {
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
