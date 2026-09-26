// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/// Defines how the destination URI of a link is written.
@NullMarked
@FunctionalInterface 
public interface JavaDocInfoLinkPrinter {
  @Contract("_,_ -> param1")
  StringBuilder printLinkURI(StringBuilder builder, PsiElement targetElement);
}
