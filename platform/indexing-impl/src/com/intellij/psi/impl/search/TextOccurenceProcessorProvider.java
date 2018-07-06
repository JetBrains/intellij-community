// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.SymbolReference;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

interface TextOccurenceProcessorProvider extends Function<Processor<? super SymbolReference>, TextOccurenceProcessor> {

  @NotNull
  @Override
  TextOccurenceProcessor apply(@NotNull Processor<? super SymbolReference> processor);
}
