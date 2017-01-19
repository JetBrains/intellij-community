/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.util.SmartList;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LineNumberConvertor {
  // Oneside -> Twoside
  @NotNull private final TreeMap<Integer, Integer> myFragments1;

  // Twoside -> Oneside
  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments1;

  @NotNull private final Corrector myCorrector = new Corrector();

  private LineNumberConvertor(@NotNull TreeMap<Integer, Integer> fragments1,
                              @NotNull TreeMap<Integer, Integer> invertedFragments1) {
    myFragments1 = fragments1;
    myInvertedFragments1 = invertedFragments1;
  }

  public int convert(int value) {
    return convert(value, true, false);
  }

  public int convertInv(int value) {
    return convert(value, false, false);
  }

  public int convertApproximate(int value) {
    return convert(value, true, true);
  }

  public int convertApproximateInv(int value) {
    return convert(value, false, true);
  }

  //
  // Impl
  //

  @NotNull
  public TIntFunction createConvertor() {
    return this::convert;
  }

  public int convert(int value, boolean fromOneside, boolean approximate) {
    return myCorrector.convertCorrected(value, fromOneside, approximate);
  }

  /**
   * Oneside was changed. We should update converters, because of changed shift.
   *
   * @param synchronous true - twoside were changed in a same way
   *                    false - twoside was not changed
   */
  public void handleOnesideChange(int startLine, int endLine, int shift, boolean synchronous) {
    myCorrector.handleOnesideChange(startLine, endLine, shift, synchronous);
  }


  /**
   * @param approximate false: return exact matching between lines, and -1 if it's impossible
   *                    true: return 'good enough' position, even if exact matching is impossible
   */
  private int convertRaw(boolean fromOneside, int value, boolean approximate) {
    TreeMap<Integer, Integer> fragments = fromOneside ? myFragments1 : myInvertedFragments1;

    if (approximate) {
      Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
      if (floor == null) return 0;
      if (floor.getValue() != -1) return floor.getValue() - floor.getKey() + value;

      Map.Entry<Integer, Integer> floorHead = fragments.floorEntry(floor.getKey() - 1);
      assert floorHead != null && floorHead.getValue() != -1;

      return floorHead.getValue() - floorHead.getKey() + floor.getKey();
    }
    else {
      Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
      if (floor == null || floor.getValue() == -1) return -1;
      return floor.getValue() - floor.getKey() + value;
    }
  }

  public static class Builder {
    @NotNull private final TreeMap<Integer, Integer> myFragments1 = new TreeMap<>();
    @NotNull private final TreeMap<Integer, Integer> myInvertedFragments1 = new TreeMap<>();

    public void put(int start, int newStart, int length) {
      myFragments1.put(start, newStart);
      myFragments1.put(start + length, -1);

      myInvertedFragments1.put(newStart, start);
      myInvertedFragments1.put(newStart + length, -1);
    }

    @NotNull
    public LineNumberConvertor build() {
      return new LineNumberConvertor(myFragments1, myInvertedFragments1);
    }
  }

  /*
   * myFragments allow to convert between Sm-So-Su, Mm-Mo-Mu, Em-Eo-Eu.
   *
   * Corrector processes information about synchronous modification B -> B' (when masterSide and oneside are modified the same way),
   * and allows to convert between Sm'-So'-Su', Mm'-Mo'-Mu', Em'-Eo'-Eu'.
   *
   *
   *        Before                             After
   *
   * masterSide     unchangedSide
   *         oneside
   *
   * Sm +       + So    + Su         Sm' +       + So'   + Su'
   *    |       |       |                |       |       |
   *    |       |       |                |       |       |
   *    |       |       |                |       |       |
   * Am +-------+ Ao    + Au         Am' +-------+ Ao'   + Au'
   *    |       |       |                |       |       |
   * Mm +   B   + Mo    + Mu         Mm' +       + Mo'   + Mu'
   *    |       |       |                |   B'  |       |
   * Bm +-------+ Bo    |                |       |       |
   *    |       |       |                |       |       |
   *    |       |       |            Bm' +-------+ Bo'   |
   *    |       |       |                |       |       |
   * Em +       + Eo    + Eu             |       |       + Eu'
   *                                     |       |
   *                                 Em' +       + Eo'
   *
   * Su == Su'; Mu == Mu'; Eu == Eu'; Au == Au'
   * Sm == Sm'; So == So'; Am == Am'; Ao == Ao'
   * Bo - Ao == Bm - Am; Bo' - Ao' == Bm' - Am'
   *
   * change.startOneside == Ao == Ao'
   * change.startTwoside == Am == Am'
   *
   * change.oldLength == Bo - Ao
   * change.newLength == Bo' - Ao'
   *
   * change.side == masterSide
   *
   * In case of multiple changes - we process them in reverse order (from new to old).
   *
   */
  private class Corrector {
    private final List<CorrectedChange> myChanges = new SmartList<>();

    @SuppressWarnings("UnnecessaryLocalVariable")
    public void handleOnesideChange(int startLine, int endLine, int shift, boolean synchronous) {
      int oldLength = endLine - startLine;
      int newLength = oldLength + shift;

      if (synchronous) {
        int oldTwosideStart = convert(startLine, true, false);
        assert oldTwosideStart != -1;

        myChanges.add(new CorrectedChange(startLine, oldTwosideStart, oldLength, newLength));
      }
      else {
        myChanges.add(new CorrectedChange(startLine, oldLength, newLength));
      }
    }

    public int convertCorrected(int value, boolean fromOneside, boolean approximate) {
      if (fromOneside) {
        return convertFromOneside(value, approximate, myChanges.size() - 1);
      }
      else {
        return convertFromTwoside(value, approximate, myChanges.size() - 1);
      }
    }

    private int convertFromTwoside(int value, boolean approximate, int index) {
      if (index < 0) {
        return convertRaw(false, value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (!change.synchronous) { // ?u' -> ?o'
        int converted = convertFromTwoside(value, approximate, index - 1);
        if (converted < change.startOneside) { // Su' -> So'
          // Su' == Su; So' == So
          // value == Su'; converted == So
          return converted;
        }
        if (converted >= change.startOneside + change.oldLength) { // Eu' -> Eo'
          // Eo' == Eo + shift; Eu' == Eu
          // value == Eu'; converted == Eo
          return converted + shift;
        }

        // Mu' -> Mo'
        if (!approximate) return -1;
        // We can't convert Mo into Mo'
        // converted == Mo
        return append(converted, Math.min(change.newLength, converted - change.startOneside));
      }
      else { // ?m '-> ?o'
        if (value < change.startTwoside) { // Sm' -> So'
          return convertFromTwoside(value, approximate, index - 1);
        }
        if (value >= change.startTwoside + change.newLength) { // Em' -> Eo'
          // Em' == Em + shift; Eo' == Eo + shift
          // value == Em'; converted == Eo
          int converted = convertFromTwoside(value - shift, approximate, index - 1);
          return append(converted, shift);
        }

        // Mm' -> Mo'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // convertedStart == Ao; value - change.startOneside == Mm' - Am'
        int convertedStart = convertFromTwoside(change.startTwoside, approximate, index - 1);
        return append(convertedStart, value - change.startTwoside);
      }
    }

    private int convertFromOneside(int value, boolean approximate, int index) {
      if (index < 0) {
        return convertRaw(true, value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (value < change.startOneside) { // So' -> Sm', So' -> Su'
        // So' == So; Sm' == Sm; Su' == Su
        // value = So'
        return convertFromOneside(value, approximate, index - 1);
      }
      if (value >= change.startOneside + change.newLength) { // Eo' -> Em', Eo' -> Eu'
        // Eo' == Eo + shift; Em' == Em + shift; Eu' == Eu
        // value = Eo'
        int converted = convertFromOneside(value - shift, approximate, index - 1);
        return append(converted, change.synchronous ? shift : 0);
      }

      if (!change.synchronous) { // Mo' -> Mu'
        if (!approximate) return -1;
        // we can't convert Mo' into Mo. And thus get valid Mu/Mu'.
        // return: Au'
        return convertFromOneside(change.startOneside, approximate, index - 1);
      }
      else { // Mo' -> Mm'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // value = Mo'
        int convertedStart = convertFromOneside(change.startOneside, approximate, index - 1);
        return append(convertedStart, value - change.startOneside);
      }
    }

    private int append(int value, int shift) {
      return value == -1 ? -1 : value + shift;
    }
  }

  private static class CorrectedChange {
    public final boolean synchronous;

    public final int startOneside;
    public final int startTwoside;
    public final int oldLength;
    public final int newLength;

    public CorrectedChange(int startOneside, int oldLength, int newLength) {
      this.synchronous = false;
      this.startTwoside = -1;

      this.startOneside = startOneside;
      this.oldLength = oldLength;
      this.newLength = newLength;
    }

    public CorrectedChange(int startOneside, int startTwoside, int oldLength, int newLength) {
      this.synchronous = true;

      this.startOneside = startOneside;
      this.startTwoside = startTwoside;
      this.oldLength = oldLength;
      this.newLength = newLength;
    }
  }
}
