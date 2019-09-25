// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import org.jetbrains.annotations.NotNull;

/**
 * Post filter for highlight infos which is used in UpdateHighlightersUtil
 * to filter out not relevant infos
 * @see UpdateHighlightersUtil
 */
public interface HighlightInfoPostFilter {

  /**
   *
   * Return true if highlighting should be added to markup
   */
  boolean accept(@NotNull HighlightInfo highlightInfo);
}