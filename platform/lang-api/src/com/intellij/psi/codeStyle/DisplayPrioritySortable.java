// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Allows to define in which order settings items must be shown.
 *
 * @apiNote applicable only for color settings and code style configurables
 * @see com.intellij.openapi.options.ex.Weighted
 */
public interface DisplayPrioritySortable {
  /**
   * @return A priority as defined in {@link DisplayPriority}
   */
  DisplayPriority getPriority();

  /**
   * The method allows to order configurables with the same {@link DisplayPriority} in addition to {@link #getPriority()}
   * method.
   *
   * @return The importance weight, defaults to 0. Bigger importance weight makes a configurable be displayed prior
   * to ones with lower importance weight.
   */
  default int getWeight() { return 0; }

  /**
   * Compare the two objects which optionally may implement {@link DisplayPriority} interface. A result of
   * {@code sort((o1, o2)->compare(o1,o2))} will contain objects ordered by the following set of rules:
   * <ul>
   *   <li>The object implementing {@link DisplayPrioritySortable} goes first.</li>
   *   <li>If both objects implement {@link DisplayPrioritySortable} they are compared by {@link DisplayPriority}. They
   *    are ordered according to the enumerated values starting with {@link DisplayPriority#GENERAL_SETTINGS}.</li>
   *    <li>Objects with the same {@link DisplayPriority} are compared by weight. The one with the bigger weight
   *    goes first.</li>
   *    <li>If the objects have the same display priority, weight or both don't implement {@link DisplayPriority},
   *    they are ordered lexicographically using {@link String#compareToIgnoreCase(String)} function.</li>
   * </ul>
   *
   * @param o1            The first object to compare.
   * @param o2            The second object to compare.
   * @param nameExtractor The name extractor function: contains a logic to extract a name to compare the objects
   * @param <T>           The actual type of the object to compare.
   * @return a negative integer if {@code o1} should go first (less then {@code o2}), 0 if the order doesn't matter or a
   * positive integer if {@code o1} goes after {@code o2}
   */
  static <T> int compare(@NotNull T o1, @NotNull T o2, @NotNull Function<? super T, String> nameExtractor) {
    if (o1 instanceof DisplayPrioritySortable) {
      if (o2 instanceof DisplayPrioritySortable) {
        DisplayPrioritySortable d1 = (DisplayPrioritySortable)o1;
        DisplayPrioritySortable d2 = (DisplayPrioritySortable)o2;
        int result = (d1.getPriority()).compareTo(d2.getPriority());
        if (result != 0) return result;
        result = -Integer.compare(d1.getWeight(), d2.getWeight());
        if (result != 0) return result;
      }
      else {
        return -1;
      }
    }
    else if (o2 instanceof DisplayPrioritySortable) {
      return 1;
    }
    return StringUtil.compare(nameExtractor.apply(o1), nameExtractor.apply(o2), true);
  }
}
