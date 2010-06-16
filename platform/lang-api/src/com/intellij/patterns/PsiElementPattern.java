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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.collection;
import static com.intellij.patterns.StandardPatterns.not;

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
    return element.getContext();
  }

  public Self withElementType(IElementType type) {
    return withElementType(PlatformPatterns.elementType().equalTo(type));
  }

  public Self withElementType(TokenSet type) {
    return withElementType(PlatformPatterns.elementType().tokenSet(type));
  }

  public Self afterLeaf(@NotNull final String... withText) {
    return afterLeaf(psiElement().withText(PlatformPatterns.string().oneOf(withText)));
  }
  
  public Self afterLeaf(@NotNull final ElementPattern<? extends PsiElement> pattern) {
    return afterLeafSkipping(psiElement().whitespaceCommentOrError(), pattern);
  }

  public Self beforeLeaf(@NotNull final ElementPattern<? extends PsiElement> pattern) {
    return beforeLeafSkipping(psiElement().whitespaceCommentOrError(), pattern);
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

  public Self withReference(final Class<? extends PsiReference> referenceClass) {
    return with(new PatternCondition<T>("withReference") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        for (final PsiReference reference : t.getReferences()) {
          if (ReflectionCache.isInstance(reference, referenceClass)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public Self inFile(@NotNull final ElementPattern<? extends PsiFile> filePattern) {
    return with(new PatternCondition<T>("inFile") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return filePattern.accepts(t.getContainingFile(), context);
      }
    });
  }

  public Self inVirtualFile(@NotNull final ElementPattern<? extends VirtualFile> filePattern) {
    return with(new PatternCondition<T>("inVirtualFile") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return filePattern.accepts(t.getContainingFile().getViewProvider().getVirtualFile(), context);
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
          do {
            while (element != null && element.getPrevSibling() == null) {
              element = element.getParent();
            }
            if (element == null) break;
            element = element.getPrevSibling();

            while (element != null && element.getLastChild() != null) {
              element = element.getLastChild();
            }
          }
          while (element != null && element.getTextLength() == 0);

          if (!skip.getCondition().accepts(element, context)) {
            return pattern.getCondition().accepts(element, context);
          }
        }
      }

    });
  }

  public Self beforeLeafSkipping(@NotNull final ElementPattern skip, @NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>("baforeLeafSkipping") {
      public boolean accepts(@NotNull T t, final ProcessingContext context) {
        PsiElement element = t;
        while (true) {
          do {
            while (element != null && element.getNextSibling() == null) {
              element = element.getParent();
            }
            if (element == null) break;
            element = element.getNextSibling();

            while (element != null && element.getFirstChild() != null) {
              element = element.getFirstChild();
            }
          }
          while (element != null && element.getTextLength() == 0);
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
    return new PatternConditionPlus<T, String>("_withText", pattern) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<String, ProcessingContext> processor) {
        return processor.process(t.getText(), context);
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

  public Self withMetaData(final ElementPattern<? extends PsiMetaData> metaDataPattern) {
    return with(new PatternCondition<T>("withMetaData") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return t instanceof PsiMetaOwner && metaDataPattern.accepts(((PsiMetaOwner)t).getMetaData(), context);
      }
    });
  }
  
  public Self referencing(final ElementPattern<? extends PsiElement> targetPattern) {
    return with(new PatternCondition<T>("referencing") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
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

  public Self compiled() {
    return with(new PatternCondition<T>("compiled") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return t instanceof PsiCompiledElement;
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
