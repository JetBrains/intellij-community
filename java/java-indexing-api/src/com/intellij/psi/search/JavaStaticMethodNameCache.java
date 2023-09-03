// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Experimental
public abstract class JavaStaticMethodNameCache {

  public static final ExtensionPointName<JavaStaticMethodNameCache> EP_NAME =
    ExtensionPointName.create("com.intellij.java.staticMethodNamesCache");

  public abstract boolean processMethodsWithName(@NotNull Predicate<String> namePredicate,
                                                 @NotNull final Processor<? super PsiMethod> processor,
                                                 @NotNull GlobalSearchScope scope,
                                                 @Nullable IdFilter filter);

  public abstract Class<? extends PsiShortNamesCache> replaced();
}
