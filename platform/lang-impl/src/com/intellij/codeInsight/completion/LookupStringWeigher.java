package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LookupStringWeigher extends CompletionWeigher {
  @Override
  public String weigh(@NotNull LookupElement element, CompletionLocation location) {
    return element.getLookupString().toLowerCase();
  }
}
