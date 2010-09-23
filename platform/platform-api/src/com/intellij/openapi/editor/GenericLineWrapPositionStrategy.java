/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import gnu.trove.*;
import org.jetbrains.annotations.NotNull;

/**
 * Highly customizable {@link LineWrapPositionStrategy} implementation.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Sep 23, 2010 12:04:52 PM
 */
public class GenericLineWrapPositionStrategy implements LineWrapPositionStrategy {

  private static final TIntIntProcedure GT_COMPARATOR = new TIntIntProcedure() {
    @Override
    public boolean execute(int a, int b) {
      return a > b;
    }
  };

  //private static final TIntIntProcedure LT_COMPARATOR = new TIntIntProcedure() {
  //  @Override
  //  public boolean execute(int a, int b) {
  //    return a < b;
  //  }
  //};

  /**
   * We consider that it's possible to wrap line on non-id symbol. However, weight of such position is expected to be less
   * than weight of wrap position bound to explicitly configured symbol.
   */
  private static final int NON_ID_WEIGHT = (Rule.DEFAULT_WEIGHT - 1) / 2;

  /** Holds symbols wrap rules by symbol. */
  private final TIntObjectHashMap<Rule> myRules = new TIntObjectHashMap<Rule>();

  @Override
  public int calculateWrapPosition(@NotNull CharSequence text,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset)
  {
    if (endOffset <= startOffset) {
      return endOffset;
    }

    // Normalization.
    int maxPreferredOffsetToUse = maxPreferredOffset >= endOffset ? endOffset - 1 : maxPreferredOffset;
    maxPreferredOffsetToUse = maxPreferredOffsetToUse < startOffset ? startOffset : maxPreferredOffsetToUse;

    TIntDoubleHashMap offset2Weight = new TIntDoubleHashMap();

    // Try to find out wrap position before preferred offset.
    for (int i = maxPreferredOffsetToUse; i > startOffset; i--) {
      char c = text.charAt(i);
      if (c == '\n') {
        return i + 1;
      }

      Rule rule = myRules.get(c);
      if (rule != null) {
        if (rule.condition == WrapCondition.BOTH || rule.condition == WrapCondition.AFTER) {
          int target = i+1;
          if (rule.symbol != ' ') {
            while(i < maxPreferredOffsetToUse && text.charAt(target) == ' ') {
              target++;
            }
          }
          if (target <= maxPreferredOffsetToUse) {
            offset2Weight.put(target, rule.weight);
          }
        }

        if (rule.condition == WrapCondition.BOTH || rule.condition == WrapCondition.BEFORE) {
          offset2Weight.put(i, rule.weight);
        }
        continue;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++.
      // Also don't wrap before non-id symbol preceded by a space - wrap on space instead;
      if (!isIdSymbol(c) && (i < startOffset + 2 || (isIdSymbol(text.charAt(i - 1)) && !myRules.contains(text.charAt(i - 1))))) {
        offset2Weight.put(i, NON_ID_WEIGHT);
      }
    }

    int result = chooseOffset(offset2Weight, GT_COMPARATOR, startOffset);
    if (result > 0) {
      return result;
    }

    // Try to find target offset that is beyond preferred offset.
    // Note that we don't consider symbol weights here and just break on the first appropriate position.
    if (!allowToBeyondMaxPreferredOffset) {
      return maxPreferredOffset;
    }
    for (int i = maxPreferredOffsetToUse + 1; i < endOffset; i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        return i + 1;
      }

      Rule rule = myRules.get(c);
      if (rule != null) {
        switch (rule.condition) {
          case BOTH:
          case BEFORE: return i;
          case AFTER: if (i < endOffset - 1) return i + 1;
        }
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++;
      if (!isIdSymbol(c) && (i >= endOffset - 1 || isIdSymbol(text.charAt(i + 1)))) {
        return i;
      }
    }

    return maxPreferredOffsetToUse;
  }

  /**
   * Registers given rule with the current strategy.
   *
   * @param rule    rule to register
   * @throws IllegalArgumentException     if another rule for the same symbol is already registered within the current strategy
   */
  public void addRule(@NotNull Rule rule) throws IllegalArgumentException {
    Rule existing = myRules.get(rule.symbol);
    if (existing != null) {
      throw new IllegalArgumentException(String.format(
        "Can't register given wrap rule (%s) within the current line wrap position strategy. Reason: another rule is already "
        + "registered for it - '%s'", rule, existing
      ));
    }
    existing = myRules.put(rule.symbol, rule);
    assert existing == null;
  }

  private static boolean isIdSymbol(char c) {
    return c == '_' || c == '$' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  /**
   * Tries to derive offset to use at the given map assuming that it contains mappings like '{@code offset -> weight}'.
   *
   * @param offset2Weight     map that holds '{@code offset -> weight}' entries (is allows to be empty)
   * @param comparator        strategy interface that is expected to return <code>'true'</code> if the first parameter
   *                          given to it is more preferred than the second
   * @param startOffset       start offset of the line that is being wrapped
   * @return                  one of the keys of the given map to use; negative value if no appropriate key is found or the map is empty
   */
  private static int chooseOffset(@NotNull TIntDoubleHashMap offset2Weight, @NotNull final TIntIntProcedure comparator,
                                  final int startOffset)
  {
    if (offset2Weight.isEmpty()) {
      return -1;
    }

    final double[] resultingWeight = new double[1];
    final int[] resultingOffset = new int[1];
    offset2Weight.forEachEntry(new TIntDoubleProcedure() {
      @Override
      public boolean execute(int offset, double weight) {
        boolean change = false;

        // Check if current candidate is certainly better than the current result.
        if (comparator.execute(offset, resultingOffset[0])) {
          if (weight >= resultingWeight[0]) {
            change = true;
          }
        }

        // Check if it's worth to use current mapping because of it's weight.
        if (!change && weight > resultingWeight[0]) {
          change = (offset - startOffset) * weight > (resultingOffset[0] - startOffset) * resultingWeight[0];
        }

        if (change) {
          resultingWeight[0] = weight;
          resultingOffset[0] = offset;
        }

        return true;
      }
    });
    return resultingOffset[0];
  }

  /**
   * Defines how wrapping may be performed for particular symbol.
   *
   * @see Rule
   */
  public enum WrapCondition {
    /** Means that wrap is allowed only after particular symbol. */
    AFTER,

    /** Means that wrap is allowed only before particular symbol. */
    BEFORE,

    /** Means that wrap is allowed before and after particular symbol. */
    BOTH
  }

  /**
   * Encapsulates information about rule to use during line wrapping.
   */
  public static class Rule {

    public static final int DEFAULT_WEIGHT = 10;

    public final char symbol;
    public final WrapCondition condition;

    /**
     * There is a possible case that there are more than one appropriate wrap positions on a line and we need to choose between them.
     * Here 'weight' characteristics comes into play.
     * <p/>
     * The general idea is that it's possible to prefer position with lower offset if it's weight is more than the one from
     * position with higher offset and distance between them is not too big.
     * <p/>
     * Current algorithm uses the <code>'weight'</code> in a following manner:
     * <p/>
     * <pre>
     * <ol>
     *   <li>Calculate product of line length on first wrap location and its weight;</li>
     *   <li>Calculate product of line length on second wrap location and its weight;</li>
     *   <li>Compare those products;</li>
     * </ol>
     * </pre>
     * <p/>
     * <b>Example</b>
     * Suppose we have two positions that define lines of length 30 and 10 symbols. Suppose that the weights are <code>'1'</code>
     * and <code>'4'</code> correspondingly.Position with greater weight is preferred because it's product is higher
     * ({@code 10 * 4 > 30 * 1})
     */
    public final double weight;

    public Rule(char symbol) {
      this(symbol, WrapCondition.BOTH, DEFAULT_WEIGHT);
    }

    public Rule(char symbol, WrapCondition condition) {
      this(symbol, condition, DEFAULT_WEIGHT);
    }

    public Rule(char symbol, double weight) {
      this(symbol, WrapCondition.BOTH, weight);
    }

    public Rule(char symbol, WrapCondition condition, double weight) {
      this.symbol = symbol;
      this.condition = condition;
      this.weight = weight;
    }
  }
}
