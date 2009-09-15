package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author peter
 */
public class PriorityWeigher extends CompletionWeigher {
  @Override
  public Double weigh(@NotNull LookupElement element, CompletionLocation location) {
    final PrioritizedLookupElement prioritized = element.as(PrioritizedLookupElement.class);
    if (prioritized != null) {
      return -prioritized.getPriority();
    }

    final LookupItem item = element.as(LookupItem.class);
    if (item != null) {
      return -item.getPriority();
    }
    return -0.0;
  }
}
