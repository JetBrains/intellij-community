/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PomReferenceProvider<T extends PsiElement> {
  public static final Key<Integer> OFFSET_IN_ELEMENT = Key.create("OFFSET_IN_ELEMENT");

  @NotNull
  public abstract PomReference[] getReferencesByElement(@NotNull T element, @NotNull final ProcessingContext context);



}
