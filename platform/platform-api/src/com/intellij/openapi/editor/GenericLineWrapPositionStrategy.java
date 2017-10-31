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

import com.intellij.openapi.project.Project;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Highly customizable {@link LineWrapPositionStrategy} implementation.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Sep 23, 2010 12:04:52 PM
 */
public class GenericLineWrapPositionStrategy implements LineWrapPositionStrategy {

  /**
   * We consider that it's possible to wrap line on non-id symbol. However, weight of such position is expected to be less
   * than weight of wrap position bound to explicitly configured symbol.
   */
  private static final int NON_ID_WEIGHT = (Rule.DEFAULT_WEIGHT - 1) / 2;

  /** Holds symbols wrap rules by symbol. */
  private final TIntObjectHashMap<Rule> myRules = new TIntObjectHashMap<>();
  private final Storage myOffset2weight = new Storage();

  @Override
  public int calculateWrapPosition(@NotNull Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean isSoftWrap)
  {
    if (endOffset <= startOffset) {
      return endOffset;
    }

    myOffset2weight.clear();
    myOffset2weight.anchor = startOffset;
    CharSequence text = document.getCharsSequence();

    // Normalization.
    int maxPreferredOffsetToUse = maxPreferredOffset >= endOffset ? endOffset - 1 : maxPreferredOffset;
    maxPreferredOffsetToUse = maxPreferredOffsetToUse < startOffset ? startOffset : maxPreferredOffsetToUse;

    // Try to find out wrap position before preferred offset.
    for (int i = Math.min(maxPreferredOffsetToUse, text.length() - 1); i > startOffset; i--) {
      char c = text.charAt(i);
      if (c == '\n') {
        return i + 1;
      }

      if (!canUseOffset(document, i, isSoftWrap)) {
        continue;
      }

      Rule rule = myRules.get(c);
      if (rule != null) {
        if (rule.condition == WrapCondition.BOTH || rule.condition == WrapCondition.AFTER) {
          int target = i+1;
          if (rule.symbol != ' ') {
            while(target < maxPreferredOffsetToUse && text.charAt(target) == ' ') {
              target++;
            }
          }
          if (target <= maxPreferredOffsetToUse) {
            myOffset2weight.store(target, rule.weight);
          }
        }

        if (rule.condition == WrapCondition.BOTH || rule.condition == WrapCondition.BEFORE) {
          myOffset2weight.store(i, rule.weight);
        }
        continue;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++.
      // Also don't wrap before non-id symbol preceded by a space - wrap on space instead;
      if (!isIdSymbol(c) && i > startOffset + 1 && isIdSymbol(text.charAt(i - 1)) && !myRules.contains(text.charAt(i - 1))) {
        myOffset2weight.store(i, NON_ID_WEIGHT);
      }
    }

    int result = chooseOffset();
    if (result > 0) {
      return result;
    }

    if (!allowToBeyondMaxPreferredOffset) {
      return -1;
    }

    // Try to find target offset that is beyond preferred offset.
    // Note that we don't consider symbol weights here and just break on the first appropriate position.
    for (int i = Math.min(maxPreferredOffsetToUse + 1, text.length() - 1); i < endOffset; i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        return i + 1;
      }

      if (!canUseOffset(document, i, isSoftWrap)) {
        continue;
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
      if (!isIdSymbol(c) && i < endOffset - 1 && isIdSymbol(text.charAt(i + 1))) {
        return i;
      }
    }

    return -1;
  }

  protected boolean canUseOffset(@NotNull Document document, int offset, boolean virtual) {
    return true;
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
   * Tries to derive offset to use from {@link #myOffset2weight} data structure assuming that it contains mappings
   * like '{@code offset -> weight}'.
   *
   * @return                  one of the keys of the given map to use; negative value if no appropriate key is found or the map is empty
   */
  private int chooseOffset() {
    if (myOffset2weight.end <= 0) {
      return -1;
    }

    final double[] resultingWeight = new double[1];
    final int[] resultingOffset = new int[1];
    for (int i = myOffset2weight.end - 1; i >= 0; i--) {
      if (myOffset2weight.data[i] == 0) {
        continue;
      }

      if (resultingWeight[0] <= 0) {
        resultingWeight[0] = myOffset2weight.data[i];
        resultingOffset[0] = i;
        continue;
      }

      if (resultingWeight[0] < myOffset2weight.data[i]) {
        boolean change = myOffset2weight.data[i] * i > resultingOffset[0] * resultingWeight[0];
        if (change) {
          resultingWeight[0] = myOffset2weight.data[i];
          resultingOffset[0] = i;
        }
      }
    }

    return resultingOffset[0] + myOffset2weight.anchor;
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
     * Current algorithm uses the {@code 'weight'} in a following manner:
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
     * Suppose we have two positions that define lines of length 30 and 10 symbols. Suppose that the weights are {@code '1'}
     * and {@code '4'} correspondingly.Position with greater weight is preferred because it's product is higher
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

  /**
   * Primitive array-based data structure that contain mappings like {@code int -> double}.
   * <p/>
   * The key is array index plus anchor; the value is array value.
   */
  private static class Storage {
    public double[] data = new double[256];
    public int anchor;
    public int end;

    public void store(int key, double value) {
      int index = key - anchor;
      if (index >= data.length) {
        int newLength = data.length << 1;
        while (newLength <= index && newLength > 0) {
          newLength <<= 1;
        }
        double[] newData = new double[newLength];
        System.arraycopy(data, 0, newData, 0, end);
        data = newData;
      }
      data[index] = value;
      if (index >= end) {
        end = index + 1;
      }
    }

    public void clear() {
      anchor = 0;
      end = 0;
      Arrays.fill(data, 0);
    }
  }
}
