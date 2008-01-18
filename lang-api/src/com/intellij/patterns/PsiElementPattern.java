/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.lang.ASTNode;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.collection;
import static com.intellij.patterns.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PsiElementPattern<T extends PsiElement,Self extends PsiElementPattern<T,Self>> extends TreeElementPattern<PsiElement,T,Self> {
  protected PsiElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected PsiElementPattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  protected PsiElement[] getChildren(@NotNull final PsiElement element) {
    return element.getChildren();
  }

  protected PsiElement getParent(@NotNull final PsiElement element) {
    return element.getParent();
  }

  public Self withElementType(IElementType type) {
    return withElementType(PlatformPatterns.elementType().equalTo(type));
  }

  public Self withElementType(TokenSet type) {
    return withElementType(PlatformPatterns.elementType().tokenSet(type));
  }

  public Self afterLeaf(@NotNull final ElementPattern pattern) {
    return afterLeafSkipping(psiElement().whitespaceCommentOrError(), pattern);
  }

  public Self whitespace() {
    return withElementType(TokenType.WHITE_SPACE);
  }

  public Self whitespaceCommentOrError() {
    return andOr(psiElement().whitespace(), psiElement(PsiComment.class), psiElement(PsiErrorElement.class));
  }

  public Self withFirstNonWhitespaceChild(@NotNull final ElementPattern pattern) {
    return withChildren(collection(PsiElement.class).filter(not(psiElement().whitespace()), collection(PsiElement.class).first(pattern)));
  }

  public Self equalTo(@NotNull final T o) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return t.getManager().areElementsEquivalent(t, o);
      }

      public String toString() {
        return "equalTo(" + o + ")";
      }
    });
  }

  public Self withElementType(final ElementPattern pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final ASTNode node = t.getNode();
        return node != null && pattern.accepts(node.getElementType(), matchingContext, traverseContext);
      }

      public String toString() {
        return "withElementType(" + pattern + ")";
      }
    });
  }

  public Self withText(@NotNull @NonNls final String text) {
    return withText(StandardPatterns.string().equalTo(text));
  }

  public Self withoutText(@NotNull final String text) {
    return withoutText(StandardPatterns.string().equalTo(text));
  }

  public Self withName(@NotNull @NonNls final String name) {
    return withName(StandardPatterns.string().equalTo(name));
  }

  public Self withName(@NotNull final ElementPattern name) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return t instanceof PsiNamedElement && name.accepts(((PsiNamedElement)t).getName(), matchingContext, traverseContext);
      }

      public String toString() {
        return "withName(" + name + ")";
      }
    });
  }

  @Nullable
  private static PsiElement getPrevLeaf(PsiElement element) {
    final int offset = element.getTextRange().getStartOffset();
    return offset == 0 ? null : element.getContainingFile().findElementAt(offset - 1);
  }

  public Self afterLeafSkipping(@NotNull final ElementPattern skip, @NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        PsiElement element = t;
        while (true) {
          final int offset = element.getTextRange().getStartOffset();
          if (offset == 0) return false;

          element = element.getContainingFile().findElementAt(offset - 1);
          if (element == null) return false;
          if (!skip.accepts(element, matchingContext, traverseContext)) {
            return pattern.accepts(element, matchingContext, traverseContext);
          }
        }
      }

      public String toString() {
        return "afterLeafSkipping(" + pattern.toString() + ")";
      }
    });
  }

  public Self withText(@NotNull final ElementPattern text) {
    return with(_withText(text));
  }

  private PatternCondition<T> _withText(final ElementPattern pattern) {
    return new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.accepts(t.getText(), matchingContext, traverseContext);
      }

      public String toString() {
        return "withText(" + pattern + ")";
      }
    };
  }

  public Self withoutText(@NotNull final ElementPattern text) {
    return without(_withText(text));
  }

  public static class Capture<T extends PsiElement> extends PsiElementPattern<T,Capture<T>> {

    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

    protected Capture(@NotNull final NullablePatternCondition condition) {
      super(condition);
    }


  }
}
