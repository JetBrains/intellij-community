// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.psi.impl.cache.impl.BaseFilterLexerUtil;
import com.intellij.psi.impl.cache.impl.IdAndToDoScannerBasedOnFilterLexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public abstract class LexerBasedIdIndexer implements LexingIdIndexer, IdAndToDoScannerBasedOnFilterLexer {
  @Override
  public final @NotNull Map<IdIndexEntry,Integer> map(@NotNull FileContent inputData) {
    return BaseFilterLexerUtil.calcIdEntries(inputData, this);
  }
}
