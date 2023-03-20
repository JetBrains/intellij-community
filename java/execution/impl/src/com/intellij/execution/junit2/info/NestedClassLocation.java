// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class NestedClassLocation extends Location<PsiClass> {
  private static final Logger LOG = Logger.getInstance(NestedClassLocation.class);
  private final Project myProject;
  private final PsiClass myNestedClass;
  private final Location<? extends PsiClass> myClassLocation;

  public NestedClassLocation(@NotNull final Project project, @NotNull final PsiClass nestedClass, @NotNull final Location<? extends PsiClass> classLocation) {
    myProject = project;
    myNestedClass = nestedClass;
    myClassLocation = classLocation;
  }

  public String getNestedInConcreteInheritor() {
    return ClassUtil.getJVMClassName(myClassLocation.getPsiElement()) + "$" + myNestedClass.getName();
  }

  public static NestedClassLocation elementInClass(final PsiClass psiElement, final PsiClass psiClass) {
    final Location<PsiClass> classLocation = PsiLocation.fromPsiElement(psiClass);
    return new NestedClassLocation(classLocation.getProject(), psiElement, classLocation);
  }

  @Override
  @NotNull
  public PsiClass getPsiElement() {
    return myNestedClass;
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

  public PsiClass getContainingClass() {
    return myClassLocation.getPsiElement();
  }

  @Override
  @NotNull
  public <T extends PsiElement> Iterator<Location<T>> getAncestors(final Class<T> ancestorClass, final boolean strict) {
    final Iterator<Location<T>> fromClass = myClassLocation.getAncestors(ancestorClass, false);
    if (strict) return fromClass;
    return new Iterator<>() {
      private boolean myFirstStep = ancestorClass.isInstance(myNestedClass);

      @Override
      public boolean hasNext() {
        return myFirstStep || fromClass.hasNext();
      }

      @Override
      public Location<T> next() {
        final Location<T> location = myFirstStep ? (Location<T>)NestedClassLocation.this : fromClass.next();
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
