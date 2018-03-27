// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.source;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class JavaDeclarationSearcher implements JvmDeclarationSearcher {

  @Override
  public void findDeclarations(@NotNull PsiElement declaringElement, @NotNull Consumer<? super JvmElement> consumer) {
    if (declaringElement instanceof PsiNameIdentifierOwner && declaringElement instanceof JvmElement) {
      consumer.accept((JvmElement)declaringElement);
    }
  }
}
