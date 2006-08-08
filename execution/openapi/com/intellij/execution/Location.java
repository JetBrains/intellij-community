/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public abstract class Location<E extends PsiElement> {
  @NonNls public static final String LOCATION = "Location";

  @NotNull public abstract E getPsiElement();
  @NotNull public abstract Project getProject();
  @NotNull public abstract <T extends PsiElement> Iterator<Location<T>> getAncestors(Class<T> ancestorClass, boolean strict);

  public OpenFileDescriptor getOpenFileDescriptor() {
    final E psiElement = getPsiElement();
    final PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return null;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return new OpenFileDescriptor(getProject(), virtualFile, psiElement.getTextOffset());
  }

  public <Ancestor extends PsiElement> Location<Ancestor> getParent(final Class<Ancestor> parentClass) {
    final Iterator<Location<PsiElement>> ancestors = getAncestors(PsiElement.class, true);
    if (!ancestors.hasNext()) return null;
    final Location<? extends PsiElement> parent = ancestors.next();
    if (parentClass.isInstance(parent.getPsiElement())) return (Location<Ancestor>)parent;
    return null;
  }

  public <T extends PsiElement> Location<T> getAncestorOrSelf(final Class<T> ancestorClass) {
    final Iterator<Location<T>> ancestors = getAncestors(ancestorClass, false);
    if (!ancestors.hasNext()) return null;
    return ancestors.next();
  }

  public <Ancestor extends PsiElement> Ancestor getParentElement(final Class<Ancestor> parentClass) {
    return safeGetPsiElement(getParent(parentClass));
  }

  public static <T extends PsiElement> T safeGetPsiElement(final Location<T> location) {
    return location != null ? location.getPsiElement() : null;
  }

  public static <T> T safeCast(final Object obj, final Class<T> expectedClass) {
    if (expectedClass.isInstance(obj)) return (T)obj;
    return null;
  }

  public PsiLocation<E> toPsiLocation() {
    return new PsiLocation<E>(getProject(), getPsiElement());
  }
}
