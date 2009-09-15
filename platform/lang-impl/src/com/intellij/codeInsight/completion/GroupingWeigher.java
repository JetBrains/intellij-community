package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GroupingWeigher extends CompletionWeigher {
  @Override
  public Integer weigh(@NotNull LookupElement element, CompletionLocation location) {
    final PrioritizedLookupElement prioritized = element.as(PrioritizedLookupElement.class);
    if (prioritized != null) {
      return -prioritized.getGrouping();
    }

    return -element.getGrouping();
  }
}
