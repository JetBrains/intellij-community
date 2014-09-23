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
  private final Tokenizer<JsonStringLiteral> ourStringLiteralTokenizer = new Tokenizer<JsonStringLiteral>() {
    @Override
    public void tokenize(@NotNull JsonStringLiteral element, TokenConsumer consumer) {
      final PlainTextSplitter textSplitter = PlainTextSplitter.getInstance();
      if (element.getText().contains("\\")) {
        final List<Pair<TextRange, String>> fragments = element.getTextFragments();
        for (Pair<TextRange, String> fragment : fragments) {
          final String fragmentText = fragment.getSecond();
          final TextRange fragmentRange = fragment.getFirst();
          if (!fragmentText.startsWith("\\")) {
            consumer.consumeToken(element, fragmentText, false, fragmentRange.getStartOffset(), TextRange.allOf(fragmentText), textSplitter);
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
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof JsonStringLiteral) {
      return ourStringLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }
}
