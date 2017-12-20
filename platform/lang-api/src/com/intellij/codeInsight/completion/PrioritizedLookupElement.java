/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.util.ClassConditionKey;

/**
 * Using <code>PrioritizedLookupElement</code> allows to plug into 3 {@link CompletionWeigher}s: "priority", "explicitProximity" and
 * "grouping". Standard weigher list includes the following ones in the specified order:
 * <ul>
 * <li>"priority", (see <code>PriorityWeigher</code> class) based on the value passed via {@link PrioritizedLookupElement#withPriority(LookupElement, double)}</li>
 * <li>"prefix", (see <code>PrefixMatchingWeigher</code> class) checks prefix matching</li>
 * <li>"stats", (see <code>StatisticsWeigher</code> class) bubbles up the most frequently used items</li>
 * <li>"explicitProximity", (see <code>ExplicitProximityWeigher</code> class) based on the value passed via {@link PrioritizedLookupElement#withExplicitProximity(LookupElement, int)}</li>
 * <li>"proximity", (see <code>LookupElementProximityWeigher</code> class)</li>
 * <li>"grouping", (see <code>GroupingWeigher</code> class) based on the value passed via {@link PrioritizedLookupElement#withGrouping(LookupElement, int)}</li>
 * </ul>
 * <code>PrioritizedLookupElement</code> is normally used when you want to control lookup sorting in simple cases, like when you have
 * control over ALL the items in lookup. Be especially careful with using {@link PrioritizedLookupElement#withPriority(LookupElement, double)}
 * as the corresponding weigher has the top precedence. Other way to control completion items order is implementing a custom {@link CompletionWeigher},
 * (see {@link CompletionService#RELEVANCE_KEY}).<br><br>
 * To debug the order of the completion items use '<code>Dump lookup element weights to log</code>' action when the completion lookup is
 * shown (Ctrl+Alt+Shift+W / Cmd+Alt+Shift+W), the action also copies the debug info to the Clipboard.
 *
 * @author peter
 * @see CompletionContributor
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
