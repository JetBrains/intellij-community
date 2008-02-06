/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

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
    return with(new PatternCondition<T>("withChildren") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(Arrays.asList(getChildren(t)), matchingContext, traverseContext);
      }
    });
  }

  public Self isFirstAcceptedChild(@NotNull final ElementPattern<? super ParentType> pattern) {
    return with(new PatternCondition<T>("isFirstAcceptedChild") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final ParentType parent = getParent(t);
        if (parent != null) {
          final ParentType[] children = getChildren(parent);
          for (ParentType child : children) {
            if (pattern.getCondition().accepts(child, matchingContext, traverseContext)) {
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
    return with(new PatternCondition<T>("withSuperParent") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        ParentType parent = t;
        for (int i = 0; i < level; i++) {
          if (parent == null) return false;
          parent = getParent(parent);
        }
        return pattern.getCondition().accepts(parent, matchingContext, traverseContext);
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
    return with(new PatternCondition<T>("inside") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        ParentType element = strict ? getParent(t) : t;
        while (element != null) {
          if (pattern.getCondition().accepts(element, matchingContext, traverseContext)) return true;
          element = getParent(element);
        }
        return false;
      }
    });
  }
}
