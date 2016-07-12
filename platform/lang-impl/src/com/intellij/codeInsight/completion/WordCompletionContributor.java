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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
public class WordCompletionContributor extends CompletionContributor implements DumbAware {

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC && shouldPerformWordCompletion(parameters)) {
      addWordCompletionVariants(result, parameters, Collections.<String>emptySet());
    }
  }

  public static void addWordCompletionVariants(CompletionResultSet result, final CompletionParameters parameters, Set<String> excludes) {
    final Set<String> realExcludes = new HashSet<String>(excludes);
    for (String exclude : excludes) {
      String[] words = exclude.split("[ \\.-]");
      if (words.length > 0 && StringUtil.isNotEmpty(words[0])) {
        realExcludes.add(words[0]);
      }
    }
    
    int startOffset = parameters.getOffset();
    final PsiElement position = parameters.getPosition();
    final CompletionResultSet javaResultSet = result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters));
    final CompletionResultSet plainResultSet = result.withPrefixMatcher(CompletionUtil.findAlphanumericPrefix(parameters));
    for (final String word : getAllWords(position, startOffset)) {
      if (!realExcludes.contains(word)) {
        final LookupElement item = LookupElementBuilder.create(word);
        javaResultSet.addElement(item);
        plainResultSet.addElement(item);
      }
    }

    addValuesFromOtherStringLiterals(result, parameters, realExcludes, position);
  }

  private static void addValuesFromOtherStringLiterals(CompletionResultSet result,
                                                       CompletionParameters parameters,
                                                       final Set<String> realExcludes, PsiElement position) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(position.getLanguage());
    if (definition == null) {
      return;
    }
    final ElementPattern<PsiElement> pattern = psiElement().withElementType(definition.getStringLiteralElements());
    final PsiElement localString = PsiTreeUtil.findFirstParent(position, false, element -> pattern.accepts(element));
    if (localString == null) {
      return;
    }
    ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(localString);
    if (manipulator == null) {
      return;
    }
    int offset = manipulator.getRangeInElement(localString).getStartOffset();
    PsiFile file = position.getContainingFile();
    final CompletionResultSet fullStringResult = result.withPrefixMatcher( file.getText().substring(offset + localString.getTextRange().getStartOffset(), parameters.getOffset()));
    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element == localString) {
          return;
        }
        if (pattern.accepts(element)) {
          element.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement each) {
              String valueText = ElementManipulators.getValueText(each);
              if (StringUtil.isNotEmpty(valueText) && !realExcludes.contains(valueText)) {
                final LookupElement item = LookupElementBuilder.create(valueText);
                fullStringResult.addElement(item);
              }
            }
          });
          return;
        }
        super.visitElement(element);
      }
    });
  }

  private static boolean shouldPerformWordCompletion(CompletionParameters parameters) {
    final PsiElement insertedElement = parameters.getPosition();
    final boolean dumb = DumbService.getInstance(insertedElement.getProject()).isDumb();
    if (dumb) {
      return true;
    }

    if (parameters.getInvocationCount() == 0) {
      return false;
    }



    final PsiFile file = insertedElement.getContainingFile();
    final CompletionData data = CompletionUtil.getCompletionDataByElement(insertedElement, file);
    if (data != null) {
      Set<CompletionVariant> toAdd = new HashSet<CompletionVariant>();
      data.addKeywordVariants(toAdd, insertedElement, file);
      for (CompletionVariant completionVariant : toAdd) {
        if (completionVariant.hasKeywordCompletions()) {
          return false;
        }
      }
    }

    final int startOffset = parameters.getOffset();

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      return false;
    }

    final PsiElement element = file.findElementAt(startOffset - 1);

    ASTNode textContainer = element != null ? element.getNode() : null;
    while (textContainer != null) {
      final IElementType elementType = textContainer.getElementType();
      if (LanguageWordCompletion.INSTANCE.isEnabledIn(elementType) || elementType == PlainTextTokenTypes.PLAIN_TEXT) {
        return true;
      }
      textContainer = textContainer.getTreeParent();
    }
    return false;
  }

  public static Set<String> getAllWords(final PsiElement context, final int offset) {
    final Set<String> words = new LinkedHashSet<String>();
    if (StringUtil.isEmpty(CompletionUtil.findJavaIdentifierPrefix(context, offset))) {
      return words;
    }

    final CharSequence chars = context.getContainingFile().getViewProvider().getContents(); // ??
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      @Override
      public void run(final CharSequence chars, @Nullable char[] charsArray, final int start, final int end) {
        if (start > offset || offset > end) {
          words.add(chars.subSequence(start, end).toString());
        }
      }
    }, chars, 0, chars.length());
    return words;
  }
}
