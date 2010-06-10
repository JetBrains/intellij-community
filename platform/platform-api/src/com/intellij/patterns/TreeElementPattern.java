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
package com.intellij.patterns;

import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class TreeElementPattern<ParentType, T extends ParentType, Self extends TreeElementPattern<ParentType, T, Self>>
  extends ObjectPattern<T, Self> {

  protected TreeElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected TreeElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  @Nullable
  protected abstract ParentType getParent(@NotNull ParentType parentType);

  protected abstract ParentType[] getChildren(@NotNull final ParentType parentType);

  public Self withParent(@NotNull final Class<? extends ParentType> type) {
    return withParent(StandardPatterns.instanceOf(type));
  }

  public Self withParent(@NotNull final ElementPattern<? extends ParentType> pattern) {
    return withSuperParent(1, pattern);
  }

  public Self withChild(@NotNull final ElementPattern<? extends ParentType> pattern) {
    return withChildren(StandardPatterns.<ParentType>collection().atLeastOne(pattern));
  }

  public Self withFirstChild(@NotNull final ElementPattern<? extends ParentType> pattern) {
    return withChildren(StandardPatterns.<ParentType>collection().first(pattern));
  }

  public Self withLastChild(@NotNull final ElementPattern<? extends ParentType> pattern) {
    return withChildren(StandardPatterns.<ParentType>collection().last(pattern));
  }

  public Self withChildren(@NotNull final ElementPattern<Collection<ParentType>> pattern) {
    return with(new PatternConditionPlus<T, Collection<ParentType>>("withChildren", pattern) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<Collection<ParentType>, ProcessingContext> processor) {
        return processor.process(Arrays.asList(getChildren(t)), context);
      }
    });
  }

  public Self isFirstAcceptedChild(@NotNull final ElementPattern<? super ParentType> pattern) {
    return with(new PatternCondition<T>("isFirstAcceptedChild") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        final ParentType parent = getParent(t);
        if (parent != null) {
          final ParentType[] children = getChildren(parent);
          for (ParentType child : children) {
            if (pattern.getCondition().accepts(child, context)) {
              return child == t;
            }
          }
        }

        return false;
      }
    });
  }

  public Self withSuperParent(final int level, @NotNull final Class<? extends ParentType> aClass) {
    return withSuperParent(level, StandardPatterns.instanceOf(aClass));
  }
  public Self withSuperParent(final int level, @NotNull final ElementPattern<? extends ParentType> pattern) {
    return with(new PatternConditionPlus<T, ParentType>("withSuperParent", pattern) {

      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<ParentType, ProcessingContext> processor) {
        ParentType parent = t;
        for (int i = 0; i < level; i++) {
          if (parent == null) return false;
          parent = getParent(parent);
        }
        return processor.process(parent, context);
      }
    });
  }

  public Self inside(@NotNull final Class<? extends ParentType> pattern) {
    return inside(StandardPatterns.instanceOf(pattern));
  }
  
  public Self inside(@NotNull final ElementPattern<? extends ParentType> pattern) {
    return inside(false, pattern);
  }

  public Self inside(final boolean strict, @NotNull final ElementPattern<? extends ParentType> pattern) {
    return with(new PatternConditionPlus<T, ParentType>("inside", pattern) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<ParentType, ProcessingContext> processor) {
        ParentType element = strict ? getParent(t) : t;
        while (element != null) {
          if (!processor.process(element, context)) return false;
          element = getParent(element);
        }
        return true;
      }
    });
  }

  /**
   * @param strict
   * @return Ensures that first elements in hierarchy accepted by patterns appear in specified order
   */
  public Self insideSequence(final boolean strict, @NotNull final ElementPattern<? extends ParentType>... patterns) {
    return with(new PatternCondition<T>("condInside") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        int i = 0;
        ParentType element = strict ? getParent(t) : t;
        while (element != null && i >= patterns.length) {
          for (int j = i; j < patterns.length; j++) {
            if (patterns[j].getCondition().accepts(t, context)) {
              if (i != j) return false;
              i++;
              break;
            }
          }
          element = getParent(element);
        }
        return false;
      }
    });
  }
}
