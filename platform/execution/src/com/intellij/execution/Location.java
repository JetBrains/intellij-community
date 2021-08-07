// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull public abstract E getPsiElement();
  @NotNull public abstract Project getProject();
  @NotNull public abstract <T extends PsiElement> Iterator<Location<T>> getAncestors(Class<T> ancestorClass, boolean strict);

  @Nullable
  public VirtualFile getVirtualFile() {
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
  
  @Nullable
  public OpenFileDescriptor getOpenFileDescriptor() {
    VirtualFile file = getVirtualFile();
    return file != null ? new OpenFileDescriptor(getProject(), file, getPsiElement().getTextOffset()) : null;
  }

  @Nullable
  public <Ancestor extends PsiElement> Location<Ancestor> getParent(Class<Ancestor> parentClass) {
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

  @Nullable
  public <Ancestor extends PsiElement> Ancestor getParentElement(Class<Ancestor> parentClass) {
    return safeGetPsiElement(getParent(parentClass));
  }

  @Nullable
  public static <T extends PsiElement> T safeGetPsiElement(Location<T> location) {
    return location != null ? location.getPsiElement() : null;
  }

  @Nullable
  public static <T> T safeCast(Object obj, Class<T> expectedClass) {
    if (expectedClass.isInstance(obj)) {
      @SuppressWarnings("unchecked") T t = (T)obj;
      return t;
    }
    return null;
  }

  @NotNull
  public PsiLocation<E> toPsiLocation() {
    return new PsiLocation<>(getProject(), getPsiElement());
  }

  @Nullable
  public abstract Module getModule();
}