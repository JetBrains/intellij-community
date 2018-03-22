// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmAnnotationTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PsiChildLink<Parent extends JvmAnnotationTreeElement, Child extends JvmAnnotationTreeElement> /*implements PsiRefElementCreator<Parent, Child>*/ {

  @Nullable
  public abstract Child findLinkedChild(@Nullable Parent parent);

  @NotNull
  public final PsiElementRef<Child> createChildRef(@NotNull Parent parent) {
    final Child existing = findLinkedChild(parent);
    if (existing != null) {
      return PsiElementRef.real(existing);
    }
    return PsiElementRef.imaginary(PsiElementRef.real(parent));
  }

  @NotNull
  public final PsiElementRef<Child> createChildRef(@NotNull PsiElementRef<? extends Parent> parentRef) {
    final Parent parent = parentRef.getPsiElement();
    if (parent != null) {
      final Child existing = findLinkedChild(parent);
      if (existing != null) {
        return PsiElementRef.real(existing);
      }
    }
    return PsiElementRef.imaginary(parentRef);
  }
}
