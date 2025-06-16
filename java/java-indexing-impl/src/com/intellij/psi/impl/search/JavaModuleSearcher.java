// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.searches.JavaModuleSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.CommonProcessors.CollectProcessor;
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
      //It is important to collect moduleNames first, then process the name -- don't do it recursively! -- it risks
      // a deadlock: processAllKeys() acquires readLock, but processElements() _could_ acquire writeLock (see
      // StubIndexEx.tryFixIndexesForProblemFiles())
      //In general: it is a bad idea to do recursive index lookups, i.e., another lookup from the lambda passed to something
      // like processAllKeys()/processElements(). Such lambdas should be a short & simple code, not complex deep-stack processing.
      // In a second case -- 'unfold' the recursive processing, as it is done here.
      CollectProcessor<String> moduleNamesCollector = new CollectProcessor<>();
      index.processAllKeys(JavaStubIndexKeys.MODULE_NAMES, moduleNamesCollector, queryParameters.getScope());
      for (String moduleName : moduleNamesCollector.getResults()) {
        boolean shouldContinue = index.processElements(
          JavaStubIndexKeys.MODULE_NAMES,
          moduleName,
          queryParameters.getProject(),
          queryParameters.getScope(),
          null,
          PsiJavaModule.class,
          consumer
        );
        if (!shouldContinue) {
          return false;
        }
      }
      return true;
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
