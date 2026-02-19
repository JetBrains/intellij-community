// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  /**
   * Processes visible outside static methods with a specified name.
   *
   * @param namePredicate the predicate used to filter method names
   * @param processor the processor to handle each matched PsiMethod
   * @param scope the search scope to limit the search
   * @param filter the optional filter to refine the search
   * @return true if all methods were successfully processed (processor return true for all methods), false otherwise
   */
  public abstract boolean processMethodsWithName(@NotNull Predicate<String> namePredicate,
                                                 final @NotNull Processor<? super PsiMethod> processor,
                                                 @NotNull GlobalSearchScope scope,
                                                 @Nullable IdFilter filter);

  /**
   * @return the subclass of PsiShortNamesCache that can be replaced by current class for static methods.
   */
  public abstract @NotNull Class<? extends PsiShortNamesCache> replaced();
}
