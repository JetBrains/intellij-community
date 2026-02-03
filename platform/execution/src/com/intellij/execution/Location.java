// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public abstract class Location<E extends PsiElement> {
  public static final DataKey<Location<?>> DATA_KEY = DataKey.create("Location");
  public static final DataKey<Location<?>[]> DATA_KEYS = DataKey.create("LocationArray");

  public abstract @NotNull E getPsiElement();
  public abstract @NotNull Project getProject();
  public abstract @NotNull <T extends PsiElement> Iterator<Location<T>> getAncestors(Class<T> ancestorClass, boolean strict);

  public @Nullable VirtualFile getVirtualFile() {
    E psiElement = getPsiElement();
    if (psiElement.isValid()) {
      if (psiElement instanceof PsiFileSystemItem) {
        return ((PsiFileSystemItem)psiElement).getVirtualFile();
      }
      PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile != null) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null && virtualFile.isValid()) {
          return virtualFile;
        }
      }
    }
    return null;
  }

  public Navigatable getNavigatable() {
    return getOpenFileDescriptor();
  }
  
  public @Nullable OpenFileDescriptor getOpenFileDescriptor() {
    VirtualFile file = getVirtualFile();
    return file != null ? new OpenFileDescriptor(getProject(), file, getPsiElement().getTextOffset()) : null;
  }

  public @Nullable <Ancestor extends PsiElement> Location<Ancestor> getParent(Class<Ancestor> parentClass) {
    Iterator<Location<PsiElement>> ancestors = getAncestors(PsiElement.class, true);
    if (ancestors.hasNext()) {
      Location<? extends PsiElement> parent = ancestors.next();
      if (parentClass.isInstance(parent.getPsiElement())) {
        @SuppressWarnings("unchecked") Location<Ancestor> location = (Location<Ancestor>)parent;
        return location;
      }
    }
    return null;
  }

  public @Nullable <Ancestor extends PsiElement> Ancestor getParentElement(Class<Ancestor> parentClass) {
    return safeGetPsiElement(getParent(parentClass));
  }

  public static @Nullable <T extends PsiElement> T safeGetPsiElement(Location<T> location) {
    return location != null ? location.getPsiElement() : null;
  }

  public static @Nullable <T> T safeCast(Object obj, Class<T> expectedClass) {
    if (expectedClass.isInstance(obj)) {
      @SuppressWarnings("unchecked") T t = (T)obj;
      return t;
    }
    return null;
  }

  public @NotNull PsiLocation<E> toPsiLocation() {
    return new PsiLocation<>(getProject(), getPsiElement());
  }

  public abstract @Nullable Module getModule();
}