// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.searches.JavaModuleSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class JavaModuleSearcher implements QueryExecutor<PsiJavaModule, JavaModuleSearch.Parameters> {
  @Override
  public boolean execute(JavaModuleSearch.@NotNull Parameters queryParameters,
                         @NotNull Processor<? super PsiJavaModule> consumer) {
    String name = queryParameters.getName();
    StubIndex index = StubIndex.getInstance();
    if (name == null) {
      return index.processAllKeys(JavaStubIndexKeys.MODULE_NAMES, moduleName -> {
        return index.processElements(JavaStubIndexKeys.MODULE_NAMES,
                                     moduleName,
                                     queryParameters.getProject(),
                                     queryParameters.getScope(),
                                     null,
                                     PsiJavaModule.class,
                                     consumer);
      }, queryParameters.getScope());
    }
    return index.processElements(JavaStubIndexKeys.MODULE_NAMES,
                                 name,
                                 queryParameters.getProject(),
                                 queryParameters.getScope(),
                                 null,
                                 PsiJavaModule.class,
                                 consumer);
  }
}
