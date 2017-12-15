/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.util.ClassConditionKey;

/**
 * Use only when you want to control lookup sorting & preference in simple cases when you have control over ALL the items in lookup.
 * When this is not the case, or sorting is too complex to be handled by single scalar,
 * please use relevance ({@link CompletionService#RELEVANCE_KEY}) & sorting ({@link CompletionService#SORTING_KEY}) weighers.
 *
 * @author peter
 */
public class PrioritizedLookupElement<T extends LookupElement> extends LookupElementDecorator<T> {
  public static final ClassConditionKey<PrioritizedLookupElement> CLASS_CONDITION_KEY =
    ClassConditionKey.create(PrioritizedLookupElement.class);

  private final double myPriority;
  private final int myExplicitProximity;
  private final int myGrouping;

  private PrioritizedLookupElement(T delegate, double priority, int explicitProximity, int grouping) {
    super(delegate);
    myPriority = priority;
    myExplicitProximity = explicitProximity;
    myGrouping = grouping;
  }

  public double getPriority() {
    return myPriority;
  }

  public int getGrouping() {
    return myGrouping;
  }

  public int getExplicitProximity() {
    return myExplicitProximity;
  }

  public static LookupElement withPriority(LookupElement element, double priority) {
    final PrioritizedLookupElement prioritized = element.as(CLASS_CONDITION_KEY);
    final LookupElement finalElement = prioritized == null ? element : prioritized.getDelegate();
    final int proximity = prioritized == null ? 0 : prioritized.getExplicitProximity();
    final int grouping = prioritized == null ? 0 : prioritized.getGrouping();
    return new PrioritizedLookupElement<>(finalElement, priority, proximity, grouping);
  }

  public static LookupElement withGrouping(LookupElement element, int grouping) {
    final PrioritizedLookupElement prioritized = element.as(CLASS_CONDITION_KEY);
    LookupElement finalElement = prioritized == null ? element : prioritized.getDelegate();
    final double priority = prioritized == null ? 0 : prioritized.getPriority();
    final int proximity = prioritized == null ? 0 : prioritized.getExplicitProximity();
    return new PrioritizedLookupElement<>(finalElement, priority, proximity, grouping);
  }

  public static LookupElement withExplicitProximity(LookupElement element, int explicitProximity) {
    final PrioritizedLookupElement prioritized = element.as(CLASS_CONDITION_KEY);
    final double priority = prioritized == null ? 0 : prioritized.getPriority();
    final int grouping = prioritized == null ? 0 : prioritized.getGrouping();
    final LookupElement finalElement = prioritized == null ? element : prioritized.getDelegate();
    return new PrioritizedLookupElement<>(finalElement, priority, explicitProximity, grouping);
  }
}
