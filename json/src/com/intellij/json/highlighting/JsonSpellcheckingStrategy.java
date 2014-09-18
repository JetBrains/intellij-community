package com.intellij.json.highlighting;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.impl.JSStringLiteralEscaper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public class JsonSpellcheckingStrategy extends SpellcheckingStrategy {
  private static final Tokenizer<JsonStringLiteral> ourTokenizer = new Tokenizer<JsonStringLiteral>() {
    @Override
    public void tokenize(@NotNull JsonStringLiteral element, TokenConsumer consumer) {
      if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) return;

      if (element.getTextLength() <= 2) return;

      final String text = StringUtil.stripQuotesAroundValue(element.getText());
      if (!text.contains("\\")) {
        consumer.consumeToken(element, PlainTextSplitter.getInstance());
      }
      else {
        StringBuilder unescapedText = new StringBuilder();
        Ref<int[]> offsetsRef = new Ref<int[]>();
        JSStringLiteralEscaper.parseStringCharacters(text, unescapedText, offsetsRef, false);
        EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unescapedText, offsetsRef.get(), 1);
      }
    }
  };

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof JsonStringLiteral) {
      return ourTokenizer;
    }
    return super.getTokenizer(element);
  }
}
