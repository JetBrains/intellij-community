// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.collection;
import static com.intellij.patterns.StandardPatterns.not;

/**
 * Provides patterns to put conditions on {@link PsiElement}.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see PlatformPatterns#psiElement()
 */
public abstract class PsiElementPattern<T extends PsiElement, Self extends PsiElementPattern<T, Self>> extends TreeElementPattern<PsiElement, T, Self> {
  protected PsiElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected PsiElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  protected PsiElement[] getChildren(final @NotNull PsiElement element) {
    return element.getChildren();
  }

  @Override
  protected PsiElement getParent(final @NotNull PsiElement element) {
    if (element instanceof PsiFile && InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment((PsiFile)element)) {
      return element.getParent();
    }
    return element.getContext();
  }

  public @NotNull Self withElementType(IElementType type) {
    return withElementType(PlatformPatterns.elementType().equalTo(type));
  }

  public @NotNull Self withElementType(TokenSet type) {
    return withElementType(PlatformPatterns.elementType().tokenSet(type));
  }

  public @NotNull Self afterLeaf(final @NlsSafe String @NotNull ... withText) {
    return afterLeaf(psiElement().withText(StandardPatterns.string().oneOf(withText)));
  }

  public @NotNull Self afterLeaf(final @NotNull ElementPattern<? extends PsiElement> pattern) {
    return afterLeafSkipping(psiElement().whitespaceCommentEmptyOrError(), pattern);
  }

  public @NotNull Self beforeLeaf(final @NlsSafe String @NotNull ... withText) {
    return beforeLeaf(psiElement().withText(StandardPatterns.string().oneOf(withText)));
  }

  public @NotNull Self beforeLeaf(final @NotNull ElementPattern<? extends PsiElement> pattern) {
    return beforeLeafSkipping(psiElement().whitespaceCommentEmptyOrError(), pattern);
  }

  public @NotNull Self whitespace() {
    return withElementType(TokenType.WHITE_SPACE);
  }

  public @NotNull Self whitespaceCommentOrError() {
    return andOr(psiElement().whitespace(), psiElement(PsiComment.class), psiElement(PsiErrorElement.class));
  }

  public @NotNull Self whitespaceCommentEmptyOrError() {
    return andOr(psiElement().whitespace(), psiElement(PsiComment.class), psiElement(PsiErrorElement.class), psiElement().withText(""));
  }

  public @NotNull Self withFirstNonWhitespaceChild(final @NotNull ElementPattern<? extends PsiElement> pattern) {
    return withChildren(collection(PsiElement.class).filter(not(psiElement().whitespace()), collection(PsiElement.class).first(pattern)));
  }

  public @NotNull Self withReference(final Class<? extends PsiReference> referenceClass) {
    return with(new PatternCondition<T>("withReference") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        for (final PsiReference reference : t.getReferences()) {
          if (referenceClass.isInstance(reference)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public @NotNull Self inFile(final @NotNull ElementPattern<? extends PsiFile> filePattern) {
    return with(new PatternCondition<T>("inFile") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return filePattern.accepts(t.getContainingFile(), context);
      }
    });
  }

  public @NotNull Self inVirtualFile(final @NotNull ElementPattern<? extends VirtualFile> filePattern) {
    return with(new PatternCondition<T>("inVirtualFile") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return filePattern.accepts(t.getContainingFile().getViewProvider().getVirtualFile(), context);
      }
    });
  }

  @Override
  public @NotNull Self equalTo(final @NotNull T o) {
    return with(new PatternCondition<T>("equalTo") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return t.getManager().areElementsEquivalent(t, o);
      }

    });
  }

  public @NotNull Self withElementType(final ElementPattern<IElementType> pattern) {
    return with(new PatternCondition<T>("withElementType") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        final ASTNode node = t.getNode();
        return node != null && pattern.accepts(node.getElementType());
      }

    });
  }

  public @NotNull Self withText(final @NotNull @NonNls String text) {
    return withText(StandardPatterns.string().equalTo(text));
  }

  public @NotNull Self withoutText(final @NotNull String text) {
    return withoutText(StandardPatterns.string().equalTo(text));
  }

  public @NotNull Self withName(final @NotNull @NonNls String name) {
    return withName(StandardPatterns.string().equalTo(name));
  }

  public @NotNull Self withName(final @NonNls String @NotNull ... names) {
    return withName(StandardPatterns.string().oneOf(names));
  }

  public @NotNull Self withName(final @NotNull ElementPattern<String> name) {
    return with(new PsiNamePatternCondition<>("withName", name));
  }

  public @NotNull Self afterLeafSkipping(final @NotNull ElementPattern skip, final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<T>("afterLeafSkipping") {
      @Override
      public boolean accepts(@NotNull T t, final ProcessingContext context) {
        PsiElement element = t;
        while (true) {
          element = PsiTreeUtil.prevLeaf(element);
          if (element != null && element.getTextLength() == 0) {
            continue;
          }

          if (!skip.accepts(element, context)) {
            return pattern.accepts(element, context);
          }
        }
      }

    });
  }

  public @NotNull Self beforeLeafSkipping(final @NotNull ElementPattern skip, final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<T>("beforeLeafSkipping") {
      @Override
      public boolean accepts(@NotNull T t, final ProcessingContext context) {
        PsiElement element = t;
        while (true) {
          element = PsiTreeUtil.nextLeaf(element);
          if (element != null && element.getTextLength() == 0) {
            continue;
          }

          if (!skip.accepts(element, context)) {
            return pattern.accepts(element, context);
          }
        }
      }

    });
  }

  public @NotNull Self atStartOf(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<T>("atStartOf") {
      @Override
      public boolean accepts(@NotNull T t, final ProcessingContext context) {
        PsiElement element = t;
        while (element != null) {
          if (pattern.accepts(element, context)) {
            return element.getTextRange().getStartOffset() == t.getTextRange().getStartOffset();
          }
          element = element.getContext();
        }
        return false;
      }
    });
  }

  public @NotNull Self withTextLength(final @NotNull ElementPattern lengthPattern) {
    return with(new PatternConditionPlus<T, Integer>("withTextLength", lengthPattern) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<? super Integer, ? super ProcessingContext> integerProcessingContextPairProcessor) {
        return integerProcessingContextPairProcessor.process(t.getTextLength(), context);
      }
    });
  }

  public @NotNull Self notEmpty() {
    return withTextLengthLongerThan(0);
  }

  public @NotNull Self withTextLengthLongerThan(final int minLength) {
    return with(new PatternCondition<T>("withTextLengthLongerThan") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return t.getTextLength() > minLength;
      }
    });
  }

  public @NotNull Self withText(final @NotNull ElementPattern text) {
    return with(_withText(text));
  }

  private @NotNull PatternCondition<T> _withText(final ElementPattern pattern) {
    return new PatternConditionPlus<T, String>("_withText", pattern) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<? super String, ? super ProcessingContext> processor) {
        return processor.process(t.getText(), context);
      }
    };
  }

  public @NotNull Self withoutText(final @NotNull ElementPattern text) {
    return without(_withText(text));
  }

  public @NotNull Self withLanguage(final @NotNull Language language) {
    return with(new PatternCondition<T>("withLanguage") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return t.getLanguage().equals(language);
      }
    });
  }

  public @NotNull Self withMetaData(final ElementPattern<? extends PsiMetaData> metaDataPattern) {
    return with(new PatternCondition<T>("withMetaData") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return t instanceof PsiMetaOwner && metaDataPattern.accepts(((PsiMetaOwner)t).getMetaData(), context);
      }
    });
  }

  public @NotNull Self referencing(final ElementPattern<? extends PsiElement> targetPattern) {
    return with(new PatternCondition<T>("referencing") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        final PsiReference[] references = t.getReferences();
        for (final PsiReference reference : references) {
          if (targetPattern.accepts(reference.resolve(), context)) return true;
          if (reference instanceof PsiPolyVariantReference) {
            for (final ResolveResult result : ((PsiPolyVariantReference)reference).multiResolve(true)) {
              if (targetPattern.accepts(result.getElement(), context)) return true;
            }
          }
        }
        return false;
      }
    });
  }

  public @NotNull Self compiled() {
    return with(new PatternCondition<T>("compiled") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return t instanceof PsiCompiledElement;
      }
    });
  }

  public @NotNull Self withTreeParent(final ElementPattern<? extends PsiElement> ancestor) {
    return with(new PatternCondition<T>("withTreeParent") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return ancestor.accepts(t.getParent(), context);
      }
    });
  }

  public @NotNull Self insideStarting(final ElementPattern<? extends PsiElement> ancestor) {
    return with(new PatternCondition<PsiElement>("insideStarting") {
      @Override
      public boolean accepts(@NotNull PsiElement start, ProcessingContext context) {
        PsiElement element = getParent(start);
        TextRange range = start.getTextRange();
        if (range == null) return false;

        int startOffset = range.getStartOffset();
        while (element != null && element.getTextRange() != null && element.getTextRange().getStartOffset() == startOffset) {
          if (ancestor.accepts(element, context)) {
            return true;
          }
          element = getParent(element);
        }
        return false;
      }
    });
  }

  public @NotNull Self withLastChildSkipping(final @NotNull ElementPattern skip, final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<T>("withLastChildSkipping") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        PsiElement last = t.getLastChild();
        while (last != null && skip.accepts(last)) {
          last = last.getPrevSibling();
        }
        return pattern.accepts(last);
      }
    });
  }

  public static class Capture<T extends PsiElement> extends PsiElementPattern<T,Capture<T>> {

    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

    protected Capture(final @NotNull InitialPatternCondition<T> condition) {
      super(condition);
    }


  }

}
