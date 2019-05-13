// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.source;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class JavaDeclarationSearcher implements JvmDeclarationSearcher {

  @NotNull
  @Override
  public Collection<JvmElement> findDeclarations(@NotNull PsiElement declaringElement) {
    return declaringElement instanceof JvmElement ? singletonList((JvmElement)declaringElement)
                                                  : emptyList();
  }
}
