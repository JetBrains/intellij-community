/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.ref;

import com.intellij.psi.PsiChildLink;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InstanceofLink<Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> extends PsiChildLink<Parent, CastTo> {
  private final PsiChildLink<Parent, Child> myDelegate;
  private final Class<CastTo> myCastTo;

  private InstanceofLink(PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    myDelegate = delegate;
    myCastTo = castTo;
  }

  @Override
  public CastTo findLinkedChild(@Nullable Parent parent) {
    final Child existing = myDelegate.findLinkedChild(parent);
    return myCastTo.isInstance(existing) ? (CastTo) existing : null;
  }

  @Override
  @NotNull
  public CastTo createChild(@NotNull Parent parent) throws IncorrectOperationException {
    return (CastTo) myDelegate.createChild(parent);
  }

  public static <Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> InstanceofLink<Parent, Child, CastTo> create(
    PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    return new InstanceofLink<>(delegate, castTo);
  }
}
