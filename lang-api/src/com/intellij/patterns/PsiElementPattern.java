/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.collection;
import static com.intellij.patterns.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PsiElementPattern<T extends PsiElement,Self extends PsiElementPattern<T,Self>> extends TreeElementPattern<PsiElement,T,Self> {
  protected PsiElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected PsiElementPattern(@NotNull final InitialPatternCondition<T> condition) {
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

  public Self afterLeaf(@NotNull final String withText) {
    return afterLeaf(psiElement().withText(withText));
  }
  
  public Self afterLeaf(@NotNull final ElementPattern<? extends PsiElement> pattern) {
    return afterLeafSkipping(psiElement().whitespaceCommentOrError(), pattern);
  }

  public Self whitespace() {
    return withElementType(TokenType.WHITE_SPACE);
  }

  public Self whitespaceCommentOrError() {
    return andOr(psiElement().whitespace(), psiElement(PsiComment.class), psiElement(PsiErrorElement.class));
  }

  public Self withFirstNonWhitespaceChild(@NotNull final ElementPattern<? extends PsiElement> pattern) {
    return withChildren(collection(PsiElement.class).filter(not(psiElement().whitespace()), collection(PsiElement.class).first(pattern)));
  }

  public Self inFile(@NotNull final ElementPattern<? extends PsiFile> filePattern) {
    return with(new PatternCondition<T>("inFile") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return filePattern.accepts(t.getContainingFile(), context);
      }
    });
  }

  public Self equalTo(@NotNull final T o) {
    return with(new PatternCondition<T>("equalTo") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return t.getManager().areElementsEquivalent(t, o);
      }

    });
  }

  public Self withElementType(final ElementPattern<IElementType> pattern) {
    return with(new PatternCondition<T>("withElementType") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        final ASTNode node = t.getNode();
        return node != null && pattern.accepts(node.getElementType());
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

  public Self withName(@NotNull final ElementPattern<String> name) {
    return with(new PsiNamePatternCondition<T>("withName", name));
  }

  public Self afterLeafSkipping(@NotNull final ElementPattern skip, @NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>("afterLeafSkipping") {
      public boolean accepts(@NotNull T t, final ProcessingContext context) {
        PsiElement element = t;
        while (true) {
          final int offset = element.getTextRange().getStartOffset();
          if (offset == 0) return false;

          element = element.getContainingFile().findElementAt(offset - 1);
          if (element == null) return false;
          if (!skip.getCondition().accepts(element, context)) {
            return pattern.getCondition().accepts(element, context);
          }
        }
      }

    });
  }

  public Self withText(@NotNull final ElementPattern text) {
    return with(_withText(text));
  }

  private PatternCondition<T> _withText(final ElementPattern pattern) {
    return new PatternCondition<T>("_withText") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return pattern.getCondition().accepts(t.getText(), context);
      }

    };
  }

  public Self withoutText(@NotNull final ElementPattern text) {
    return without(_withText(text));
  }

  public Self withLanguage(@NotNull final Language language) {
    return with(new PatternCondition<T>("withLanguage") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return t.getLanguage().equals(language);
      }
    });
  }

  public static class Capture<T extends PsiElement> extends PsiElementPattern<T,Capture<T>> {

    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

    protected Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }


  }

}
