// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.source;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class JavaDeclarationSearcher implements JvmDeclarationSearcher {

  @NotNull
  @Override
  public Collection<JvmElement> findDeclarations(@NotNull PsiElement declaringElement) {
    return declaringElement instanceof JvmElement ? singletonList((JvmElement)declaringElement)
                                                  : emptyList();
  }

  @Override
  public @Nullable PsiElement adjustIdentifierElement(@NotNull PsiElement identifierElement) {
    PsiElement parent = identifierElement.getParent();
    return parent instanceof PsiAnonymousClass &&
           ((PsiAnonymousClass)parent).getBaseClassReference() == identifierElement ? parent : null;
  }
}
