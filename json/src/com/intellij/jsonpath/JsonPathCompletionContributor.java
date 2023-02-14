// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.jsonpath.psi.*;
import com.intellij.jsonpath.ui.JsonPathEvaluateManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.jsonpath.JsonPathConstants.STANDARD_FUNCTIONS;
import static com.intellij.jsonpath.JsonPathConstants.STANDARD_NAMED_OPERATORS;
import static com.intellij.jsonpath.psi.JsonPathTokenSets.JSONPATH_DOT_NAVIGATION_SET;
import static com.intellij.jsonpath.psi.JsonPathTokenSets.JSONPATH_EQUALITY_OPERATOR_SET;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

public final class JsonPathCompletionContributor extends CompletionContributor {

  public JsonPathCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().withParent(JsonPathStringLiteral.class)
             .inside(psiElement().withElementType(JsonPathTypes.QUOTED_PATHS_LIST)),
           new JsonKeysCompletionProvider(false));

    ElementPattern<PsiElement> identifierPattern = or(
      psiElement().afterLeaf(psiElement().withElementType(JSONPATH_DOT_NAVIGATION_SET)),
      psiElement().withElementType(JsonPathTypes.IDENTIFIER)
    );

    extend(CompletionType.BASIC, identifierPattern, new JsonKeysCompletionProvider(true));

    extend(CompletionType.BASIC, identifierPattern, new FunctionNamesCompletionProvider());

    extend(CompletionType.BASIC,
           psiElement().withParent(JsonPathBinaryConditionalOperator.class),
           new OperatorCompletionProvider());

    KeywordsCompletionProvider keywordsCompletionProvider = new KeywordsCompletionProvider();

    extend(CompletionType.BASIC,
           psiElement().withParent(JsonPathObjectValue.class)
             .afterLeaf(psiElement().withElementType(JsonPathTypes.COLON)),
           keywordsCompletionProvider);

    extend(CompletionType.BASIC,
           psiElement()
             .afterLeaf(psiElement().withElementType(JSONPATH_EQUALITY_OPERATOR_SET))
             .andNot(psiElement().withParent(JsonPathStringLiteral.class)),
           keywordsCompletionProvider);
  }

  private static class FunctionNamesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create("*").bold());

      for (Map.Entry<String, String> function : STANDARD_FUNCTIONS.entrySet()) {
        result.addElement(LookupElementBuilder.create(function.getKey() + "()")
                            .withPresentableText(function.getKey())
                            .withIcon(AllIcons.Nodes.Method)
                            .withTailText("()")
                            .withTypeText(function.getValue()));
      }
    }
  }

  private static class KeywordsCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final String[] KEYWORDS = new String[]{"null", "true", "false"};

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String keyword : KEYWORDS) {
        result.addElement(LookupElementBuilder.create(keyword).bold());
      }
    }
  }

  private static class OperatorCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String keyword : STANDARD_NAMED_OPERATORS) {
        result.addElement(LookupElementBuilder.create(keyword).bold());
      }
    }
  }

  private static class JsonKeysCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final boolean validIdentifiersOnly;
    private static final Pattern VALID_IDENTIFIER_PATTERN = Pattern.compile("[\\w_][\\w_0-9]*", Pattern.UNICODE_CHARACTER_CLASS);

    private JsonKeysCompletionProvider(boolean validIdentifiersOnly) {
      this.validIdentifiersOnly = validIdentifiersOnly;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiFile file = parameters.getOriginalFile();

      InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
      PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(file);
      if (injectionHost != null) {
        PsiFile hostFile = injectionHost.getContainingFile();
        if (hostFile != null) {
          visitJsonPathLiteralsInFile(injectedLanguageManager, hostFile, propertyName -> {
            addCompletionElement(result, propertyName);
          });
        }
      }

      Supplier<JsonFile> targetFileGetter = file.getUserData(JsonPathEvaluateManager.JSON_PATH_EVALUATE_SOURCE_KEY);
      if (targetFileGetter != null) {
        JsonFile targetFile = targetFileGetter.get();
        if (targetFile == null) return; // it could be already removed

        targetFile.accept(new JsonRecursiveElementVisitor() {
          @Override
          public void visitProperty(@NotNull JsonProperty o) {
            super.visitProperty(o);

            String propertyName = o.getName();
            if (!propertyName.isBlank()) {
              addCompletionElement(result, propertyName);
            }
          }
        });
      }
    }

    private void addCompletionElement(@NotNull CompletionResultSet result, String propertyName) {
      if (!validIdentifiersOnly || VALID_IDENTIFIER_PATTERN.matcher(propertyName).matches()) {
        result.addElement(PrioritizedLookupElement.withPriority(
          LookupElementBuilder.create(propertyName)
            .withIcon(AllIcons.Nodes.Field)
            .withTypeText(JsonBundle.message("jsonpath.completion.key")),
          100));
      }
    }

    private void visitJsonPathLiteralsInFile(InjectedLanguageManager injectedLanguageManager,
                                             PsiFile hostFile,
                                             Consumer<String> pathNameConsumer) {
      hostFile.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);

          if (element instanceof PsiLanguageInjectionHost) {
            List<Pair<PsiElement, TextRange>> files = injectedLanguageManager.getInjectedPsiFiles(element);
            if (files == null) return;

            for (Pair<PsiElement, TextRange> rangePair : files) {
              if (rangePair.getFirst() instanceof JsonPathFile jsonPathFile) {
                visitJsonPathLiterals(jsonPathFile);
              }
            }
          }
        }

        private void visitJsonPathLiterals(JsonPathFile jsonPathFile) {
          jsonPathFile.accept(new JsonPathRecursiveElementVisitor() {
            @Override
            public void visitIdSegment(@NotNull JsonPathIdSegment o) {
              super.visitIdSegment(o);

              JsonPathId id = o.getId();
              if (!id.getTextRange().isEmpty()) {
                String literalText = getElementTextWithoutHostEscaping(id);
                if (!StringUtil.isEmptyOrSpaces(literalText)) {
                  pathNameConsumer.accept(literalText);
                }
              }
            }

            @Override
            public void visitExpressionSegment(@NotNull JsonPathExpressionSegment o) {
              super.visitExpressionSegment(o);

              JsonPathQuotedPathsList quotedPathsList = o.getQuotedPathsList();
              if (quotedPathsList == null) return;

              for (JsonPathStringLiteral stringLiteral : quotedPathsList.getStringLiteralList()) {
                String literalText = getElementTextWithoutHostEscaping(stringLiteral);
                if (literalText != null) {
                  String literalValue = StringUtil.unquoteString(literalText);
                  if (!StringUtil.isEmptyOrSpaces(literalValue)) {
                    pathNameConsumer.accept(literalValue);
                  }
                }
              }
            }

            private String getElementTextWithoutHostEscaping(@NotNull PsiElement element) {
              if (injectedLanguageManager.isInjectedFragment(element.getContainingFile())) {
                return injectedLanguageManager.getUnescapedText(element);
              }
              else {
                return element.getText();
              }
            }
          });
        }
      });
    }
  }
}
