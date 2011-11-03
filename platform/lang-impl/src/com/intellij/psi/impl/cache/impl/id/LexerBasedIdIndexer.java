/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedIdIndexer extends FileTypeIdIndexer {
  
  @Override
  @NotNull
  public final Map<IdIndexEntry,Integer> map(final FileContent inputData) {
    final IdDataConsumer consumer = new IdDataConsumer();
    final Lexer lexer = createLexer(new OccurrenceToIdDataConsumerAdapter(consumer));
    final CharSequence chars = inputData.getContentAsText();
    lexer.start(chars);
    while (lexer.getTokenType() != null) {
      lexer.advance();
    }
    return consumer.getResult();
  }

  protected abstract Lexer createLexer(BaseFilterLexer.OccurrenceConsumer consumer);

  private static class OccurrenceToIdDataConsumerAdapter implements BaseFilterLexer.OccurrenceConsumer{
    private final IdDataConsumer myIndexDataConsumer;
    
    public OccurrenceToIdDataConsumerAdapter(final IdDataConsumer indexDataConsumer) {
      myIndexDataConsumer = indexDataConsumer;
    }

    @Override
    public void addOccurrence(final CharSequence charSequence, char[] charArray, final int start, final int end, final int occurrenceMask) {
      if (charArray != null) {
        myIndexDataConsumer.addOccurrence(charArray, start, end, occurrenceMask);
      } else {
        myIndexDataConsumer.addOccurrence(charSequence, start, end, occurrenceMask);
      }
    }

    @Override
    public void incTodoOccurrence(final IndexPattern pattern) {
      // empty
    }
  
    @Override
    public boolean canConsumeTodoOccurrences() {
      return false;
    }
  }
}
