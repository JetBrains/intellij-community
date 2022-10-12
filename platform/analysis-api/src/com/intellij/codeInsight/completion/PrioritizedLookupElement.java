// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.util.ClassConditionKey;
import org.jetbrains.annotations.NotNull;

/**
 * Using {@code PrioritizedLookupElement} allows plugging into 3 {@link CompletionWeigher}s: "priority", "explicitProximity" and
 * "grouping". Standard weigher list includes the following ones in the specified order:
 * <ul>
 * <li>"priority", (see {@link PriorityWeigher} class) based on the value passed via {@link PrioritizedLookupElement#withPriority(LookupElement, double)}</li>
 * <li>"prefix", (see {@link PrefixMatchingWeigher} class) checks prefix matching</li>
 * <li>"stats", (see {@link StatisticsWeigher} class) bubbles up the most frequently used items</li>
 * <li>"explicitProximity", (see {@link ExplicitProximityWeigher} class) based on the value passed via {@link PrioritizedLookupElement#withExplicitProximity(LookupElement, int)}</li>
 * <li>"proximity", (see {@link LookupElementProximityWeigher} class)</li>
 * <li>"grouping", (see {@link GroupingWeigher} class) based on the value passed via {@link PrioritizedLookupElement#withGrouping(LookupElement, int)}</li>
 * </ul>
 * {@code PrioritizedLookupElement} is normally used when you want to control lookup sorting in simple cases, like when you have
 * control over ALL the items in lookup. Be especially careful with using {@link PrioritizedLookupElement#withPriority(LookupElement, double)}
 * as the corresponding weigher has the top precedence. Other way to control completion items order is implementing a custom {@link CompletionWeigher},
 * (see {@link CompletionService#RELEVANCE_KEY}).<br><br>
 * To debug the order of the completion items use '{@code Dump lookup element weights to log}' action when the completion lookup is
 * shown (Ctrl+Alt+Shift+W / Cmd+Alt+Shift+W), the action also copies the debug info to the Clipboard.
 *
 * @see CompletionContributor
 */
public final class PrioritizedLookupElement<T extends LookupElement> extends LookupElementDecorator<T> {
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final ClassConditionKey<PrioritizedLookupElement<?>> CLASS_CONDITION_KEY =
    (ClassConditionKey)ClassConditionKey.create(PrioritizedLookupElement.class);

  private final double myPriority;
  private final int myExplicitProximity;
  private final int myGrouping;

  private PrioritizedLookupElement(@NotNull T delegate, double priority, int explicitProximity, int grouping) {
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

  /**
   * @param element element
   * @param priority priority (higher priority puts the item closer to the beginning of the list)
   * @return decorated lookup element
   */
  public static @NotNull LookupElement withPriority(@NotNull LookupElement element, double priority) {
    PrioritizedLookupElement<?> prioritized = element.as(CLASS_CONDITION_KEY);
    LookupElement finalElement = prioritized != element ? element : prioritized.getDelegate();
    int proximity = prioritized == null ? 0 : prioritized.getExplicitProximity();
    int grouping = prioritized == null ? 0 : prioritized.getGrouping();
    return new PrioritizedLookupElement<>(finalElement, priority, proximity, grouping);
  }

  public static @NotNull LookupElement withGrouping(@NotNull LookupElement element, int grouping) {
    PrioritizedLookupElement<?> prioritized = element.as(CLASS_CONDITION_KEY);
    LookupElement finalElement = prioritized != element ? element : prioritized.getDelegate();
    double priority = prioritized == null ? 0 : prioritized.getPriority();
    int proximity = prioritized == null ? 0 : prioritized.getExplicitProximity();
    return new PrioritizedLookupElement<>(finalElement, priority, proximity, grouping);
  }

  public static @NotNull LookupElement withExplicitProximity(@NotNull LookupElement element, int explicitProximity) {
    PrioritizedLookupElement<?> prioritized = element.as(CLASS_CONDITION_KEY);
    double priority = prioritized == null ? 0 : prioritized.getPriority();
    int grouping = prioritized == null ? 0 : prioritized.getGrouping();
    LookupElement finalElement = prioritized != element ? element : prioritized.getDelegate();
    return new PrioritizedLookupElement<>(finalElement, priority, explicitProximity, grouping);
  }
}
