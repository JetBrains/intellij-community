package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class PredefinedCompletionWeigher extends CompletionWeigher {

  public static final Key<CompletionWeigher> KEY = Key.create("PredefinedCompletionWeigher");

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    CompletionWeigher weigher = element.getUserData(KEY);

    if (weigher != null) {
      return weigher.weigh(element, location);
    }

    return null;
  }
}
