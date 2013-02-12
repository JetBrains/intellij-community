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
  public static final ClassConditionKey<PrioritizedLookupElement> CLASS_CONDITION_KEY = ClassConditionKey.create(PrioritizedLookupElement.class);
  private final double myPriority;
  private final int myGrouping;
  private final int myExplicitProximity;

  private PrioritizedLookupElement(T delegate, double priority, int grouping) {
    this(delegate, priority, grouping, 0);
  }

  private PrioritizedLookupElement(T delegate, double priority, int grouping, int explicitProximity) {
    super(delegate);
    myPriority = priority;
    myGrouping = grouping;
    myExplicitProximity = explicitProximity;
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
    return new PrioritizedLookupElement<LookupElement>(element, priority, prioritized == null ? 0 : prioritized.getGrouping());
  }

  public static LookupElement withGrouping(LookupElement element, int grouping) {
    final PrioritizedLookupElement prioritized = element.as(CLASS_CONDITION_KEY);
    return new PrioritizedLookupElement<LookupElement>(element, prioritized == null ? 0 : prioritized.getPriority(), grouping);
  }

  public static LookupElement withExplicitProximity(LookupElement element, int explicitProximity) {
    final PrioritizedLookupElement prioritized = element.as(CLASS_CONDITION_KEY);
    double priority = prioritized == null ? 0 : prioritized.getPriority();
    int grouping = prioritized == null ? 0 : prioritized.getGrouping();
    return new PrioritizedLookupElement<LookupElement>(element, priority, grouping, explicitProximity);
  }
}
