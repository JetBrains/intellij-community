// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * Provides patterns for tree-like objects.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see PsiElementPattern
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

  @SafeVarargs
  public final Self withParents(final Class<? extends ParentType> @NotNull ... types) {
    return with(new PatternCondition<T>("withParents") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        ParentType current = getParent(t);
        for (Class<? extends ParentType> type : types) {
          if (current == null || !type.isInstance(current)) {
            return false;
          }
          current = getParent(current);
        }
        return true;
      }
    });
  }
  public Self withParent(@NotNull final Class<? extends ParentType> type) {
    return withParent(StandardPatterns.instanceOf(type));
  }

  @NotNull
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
                                   PairProcessor<? super Collection<ParentType>, ? super ProcessingContext> processor) {
        return processor.process(Arrays.asList(getChildren(t)), context);
      }
    });
  }

  public Self isFirstAcceptedChild(@NotNull final ElementPattern<? super ParentType> pattern) {
    return with(new PatternCondition<T>("isFirstAcceptedChild") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        final ParentType parent = getParent(t);
        if (parent != null) {
          final ParentType[] children = getChildren(parent);
          for (ParentType child : children) {
            if (pattern.accepts(child, context)) {
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
    return with(new PatternConditionPlus<T, ParentType>(level == 1 ? "withParent" : "withSuperParent", pattern) {

      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<? super ParentType, ? super ProcessingContext> processor) {
        ParentType parent = t;
        for (int i = 0; i < level; i++) {
          if (parent == null) return true;
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
                                   PairProcessor<? super ParentType, ? super ProcessingContext> processor) {
        ParentType element = strict ? getParent(t) : t;
        while (element != null) {
          if (!processor.process(element, context)) return false;
          element = getParent(element);
        }
        return true;
      }
    });
  }

  public Self withAncestor(final int levelsUp, @NotNull final ElementPattern<? extends ParentType> pattern) {
    return with(new PatternCondition<T>("withAncestor") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        ParentType element = t;
        for (int i=0; i<levelsUp+1;i++) {
          if (pattern.accepts(element, context)) return true;
          element = getParent(element);
          if (element == null) break;
        }
        return false;
      }
    });
  }

  public Self inside(final boolean strict, @NotNull final ElementPattern<? extends ParentType> pattern,
                     @NotNull final ElementPattern<? extends ParentType> stopAt) {
    return with(new PatternCondition<T>("inside") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        ParentType element = strict ? getParent(t) : t;
        while (element != null) {
          if (stopAt.accepts(element, context)) return false;
          if (pattern.accepts(element, context)) return true;
          element = getParent(element);
        }
        return false;
      }
    });
  }

  /**
   * @return Ensures that first elements in hierarchy accepted by patterns appear in specified order
   */
  @SafeVarargs
  public final Self insideSequence(final boolean strict, final ElementPattern<? extends ParentType> @NotNull ... patterns) {
    return with(new PatternCondition<T>("insideSequence") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        int i = 0;
        ParentType element = strict ? getParent(t) : t;
        while (element != null && i < patterns.length) {
          for (int j = i; j < patterns.length; j++) {
            if (patterns[j].accepts(element, context)) {
              if (i != j) return false;
              i++;
              break;
            }
          }
          element = getParent(element);
        }
        return true;
      }
    });
  }

  public Self afterSibling(final ElementPattern<? extends ParentType> pattern) {
    return with(new PatternCondition<T>("afterSibling") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        final ParentType parent = getParent(t);
        if (parent == null) return false;
        final ParentType[] children = getChildren(parent);
        final int i = Arrays.asList(children).indexOf(t);
        if (i <= 0) return false;
        return pattern.accepts(children[i - 1], context);
      }
    });
  }

  public Self afterSiblingSkipping(@NotNull final ElementPattern skip, final ElementPattern<? extends ParentType> pattern) {
    return with(new PatternCondition<T>("afterSiblingSkipping") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        final ParentType parent = getParent(t);
        if (parent == null) return false;
        final ParentType[] children = getChildren(parent);
        int i = Arrays.asList(children).indexOf(t);
        while (--i >= 0) {
          if (!skip.accepts(children[i], context)) {
            return pattern.accepts(children[i], context);
          }
        }
        return false;
      }
    });
  }
}

