// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.searches.ImplicitClassSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class ImplicitClassesSearcher implements QueryExecutor<PsiImplicitClass, ImplicitClassSearch.Parameters> {
  @Override
  public boolean execute(ImplicitClassSearch.@NotNull Parameters queryParameters,
                         @NotNull Processor<? super PsiImplicitClass> consumer) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.IMPLICIT_CLASSES,
                                                   queryParameters.getName(),
                                                   queryParameters.getProject(),
                                                   queryParameters.getScope(),
                                                   null,
                                                   PsiImplicitClass.class,
                                                   consumer);
  }
}
