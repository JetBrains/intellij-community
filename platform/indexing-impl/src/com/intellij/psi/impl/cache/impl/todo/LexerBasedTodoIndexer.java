// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.psi.impl.cache.impl.BaseFilterLexerUtil;
import com.intellij.psi.impl.cache.impl.IdAndToDoScannerBasedOnFilterLexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *
 * @see com.intellij.psi.impl.search.IndexPatternBuilder
 */
public abstract class LexerBasedTodoIndexer extends VersionedTodoIndexer implements IdAndToDoScannerBasedOnFilterLexer {
  @Override
  public @NotNull Map<TodoIndexEntry,Integer> map(final @NotNull FileContent inputData) {
    return BaseFilterLexerUtil.calcTodoEntries(inputData, this);
  }
}
