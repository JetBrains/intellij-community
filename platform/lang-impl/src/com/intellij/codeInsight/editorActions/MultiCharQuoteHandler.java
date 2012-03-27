/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public interface MultiCharQuoteHandler extends QuoteHandler {
  /**
   * returns closing quote by opening quote which is placed immediately before offset. If there is no quote or the quote is equivalent
   * to opening quote the method should return null
   */
  @Nullable
  CharSequence getClosingQuote(HighlighterIterator iterator, int offset);
}
