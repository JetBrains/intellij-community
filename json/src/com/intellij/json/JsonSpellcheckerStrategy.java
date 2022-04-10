package com.intellij.json;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerStrategy extends SpellcheckingStrategy {
  private final Tokenizer<JsonStringLiteral> ourStringLiteralTokenizer = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull JsonStringLiteral element, TokenConsumer consumer) {
      final PlainTextSplitter textSplitter = PlainTextSplitter.getInstance();
      if (element.textContains('\\')) {
        final List<Pair<TextRange, String>> fragments = element.getTextFragments();
        for (Pair<TextRange, String> fragment : fragments) {
          final TextRange fragmentRange = fragment.getFirst();
          final String escaped = fragment.getSecond();
          // Fragment without escaping, also not a broken escape sequence or a unicode code point
          if (escaped.length() == fragmentRange.getLength() && !escaped.startsWith("\\")) {
            consumer.consumeToken(element, escaped, false, fragmentRange.getStartOffset(), TextRange.allOf(escaped), textSplitter);
          }
        }
      }
      else {
        consumer.consumeToken(element, textSplitter);
      }
    }
  };

  @NotNull
  @Override
  public Tokenizer<?> getTokenizer(PsiElement element) {
    if (element instanceof JsonStringLiteral) {
      return new JsonSchemaSpellcheckerClientForJson((JsonStringLiteral)element).matchesNameFromSchema()
        ? EMPTY_TOKENIZER
        : ourStringLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }

  private static class JsonSchemaSpellcheckerClientForJson extends JsonSchemaSpellcheckerClient {
    @NotNull private final JsonStringLiteral element;

    protected JsonSchemaSpellcheckerClientForJson(@NotNull JsonStringLiteral element) {
      this.element = element;
    }

    @Override
    protected @NotNull JsonStringLiteral getElement() {
      return element;
    }

    @Override
    protected @NotNull String getValue() {
      return element.getValue();
    }
  }
}
