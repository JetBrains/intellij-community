// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PsiLocation<E extends PsiElement> extends Location<E> {
  private static final Logger LOG = Logger.getInstance(PsiLocation.class);
  private final E myPsiElement;
  private final Project myProject;
  private final Module myModule;

  public PsiLocation(E psiElement) {
    this(psiElement.getProject(), psiElement);
  }

  public PsiLocation(final @NotNull Project project, final @NotNull E psiElement) {
    myPsiElement = psiElement;
    myProject = project;
    myModule = ModuleUtilCore.findModuleForPsiElement(psiElement);
  }

  public PsiLocation(@NotNull Project project, Module module, @NotNull E psiElement) {
    myPsiElement = psiElement;
    myProject = project;
    myModule = module;
  }

  @Override
  public @NotNull E getPsiElement() {
    return myPsiElement;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public Module getModule() {
    return myModule;
  }

  @Override
  public @NotNull <T extends PsiElement> Iterator<Location<T>> getAncestors(final @NotNull Class<T> ancestorClass, final boolean strict) {
    final T first = strict || !ancestorClass.isInstance(myPsiElement) ? findNext(myPsiElement, ancestorClass) : (T)myPsiElement;
    return new Iterator<>() {
      private T myCurrent = first;

      @Override
      public boolean hasNext() {
        return myCurrent != null;
      }

      @Override
      public Location<T> next() {
        if (myCurrent == null) throw new NoSuchElementException();
        final PsiLocation<T> psiLocation = new PsiLocation<>(myProject, myCurrent);
        myCurrent = findNext(myCurrent, ancestorClass);
        return psiLocation;
      }

      @Override
      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }

  @Override
  public @NotNull PsiLocation<E> toPsiLocation() {
    return this;
  }

  private static <ElementClass extends PsiElement> ElementClass findNext(final PsiElement psiElement, final Class<ElementClass> ancestorClass) {
    PsiElement element = psiElement;
    while ((element = element.getParent()) != null && !(element instanceof PsiFile)) {
      final ElementClass ancestor = safeCast(element, ancestorClass);
      if (ancestor != null) return ancestor;
    }
    return null;
  }

  public static <T extends PsiElement> Location<T> fromPsiElement(@NotNull Project project, final T element) {
    if (element == null) return null;
    return new PsiLocation<>(project, element);
  }

  public static <T extends PsiElement> Location<T> fromPsiElement(final T element) {
    return fromPsiElement(element, null);
  }

  public static <T extends PsiElement> Location<T> fromPsiElement(T element, Module module) {
    if (element == null) return null;
    PsiUtilCore.ensureValid(element);
    return module != null ? new PsiLocation<>(element.getProject(), module, element) : new PsiLocation<>(element.getProject(), element);
  }
}
