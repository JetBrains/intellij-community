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

import com.intellij.diff.util.Side;
import com.intellij.util.SmartList;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class LineNumberConvertor {
  // Oneside -> Twoside
  @NotNull private final TreeMap<Integer, Integer> myFragments1;
  @NotNull private final TreeMap<Integer, Integer> myFragments2;

  // Twoside -> Oneside
  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments1;
  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments2;

  @NotNull private final Corrector myCorrector = new Corrector();

  public LineNumberConvertor(@NotNull TreeMap<Integer, Integer> fragments1,
                             @NotNull TreeMap<Integer, Integer> fragments2,
                             @NotNull TreeMap<Integer, Integer> invertedFragments1,
                             @NotNull TreeMap<Integer, Integer> invertedFragments2) {
    myFragments1 = fragments1;
    myFragments2 = fragments2;
    myInvertedFragments1 = invertedFragments1;
    myInvertedFragments2 = invertedFragments2;
  }

  public int convert1(int value) {
    return convert(value, Side.LEFT, true, false);
  }

  public int convert2(int value) {
    return convert(value, Side.RIGHT, true, false);
  }

  public int convertInv1(int value) {
    return convert(value, Side.LEFT, false, false);
  }

  public int convertInv2(int value) {
    return convert(value, Side.RIGHT, false, false);
  }

  public int convertApproximate1(int value) {
    return convert(value, Side.LEFT, true, true);
  }

  public int convertApproximate2(int value) {
    return convert(value, Side.RIGHT, true, true);
  }

  public int convertApproximateInv1(int value) {
    return convert(value, Side.LEFT, false, true);
  }

  public int convertApproximateInv2(int value) {
    return convert(value, Side.RIGHT, false, true);
  }

  //
  // Impl
  //

  @NotNull
  public TIntFunction createConvertor1() {
    return new TIntFunction() {
      @Override
      public int execute(int value) {
        return convert1(value);
      }
    };
  }

  @NotNull
  public TIntFunction createConvertor2() {
    return new TIntFunction() {
      @Override
      public int execute(int value) {
        return convert2(value);
      }
    };
  }

  private int convert(int value, @NotNull Side side, boolean fromOneside, boolean approximate) {
    return myCorrector.convertCorrected(value, side, fromOneside, approximate);
  }

  /*
   * Both oneside and one of the sides were changed in a same way. We should update converters, because of changed shift.
   */
  public void handleOnesideChange(int startLine, int endLine, int shift, @NotNull Side masterSide) {
    myCorrector.handleOnesideChange(startLine, endLine, shift, masterSide);
  }


  private static int convert(@NotNull final TreeMap<Integer, Integer> fragments, int value, boolean approximate) {
    return approximate ? convertApproximate(fragments, value) : convert(fragments, value);
  }

  /*
   * This convertor returns exact matching between lines, and -1 if it's impossible
   */
  private static int convert(@NotNull final TreeMap<Integer, Integer> fragments, int value) {
    Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
    if (floor == null || floor.getValue() == -1) return -1;
    return floor.getValue() - floor.getKey() + value;
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  private static int convertApproximate(@NotNull final TreeMap<Integer, Integer> fragments, int value) {
    Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
    if (floor == null) return 0;
    if (floor.getValue() != -1) return floor.getValue() - floor.getKey() + value;

    Map.Entry<Integer, Integer> floorHead = fragments.floorEntry(floor.getKey() - 1);
    assert floorHead != null && floorHead.getValue() != -1;

    return floorHead.getValue() - floorHead.getKey() + floor.getKey();
  }

  @NotNull
  private TreeMap<Integer, Integer> getFragments(@NotNull Side side, boolean fromOneside) {
    return fromOneside ? side.select(myFragments1, myFragments2)
                       : side.select(myInvertedFragments1, myInvertedFragments2);
  }

  public static class Builder {
    @NotNull private final TreeMap<Integer, Integer> myFragments1 = new TreeMap<Integer, Integer>();
    @NotNull private final TreeMap<Integer, Integer> myFragments2 = new TreeMap<Integer, Integer>();

    @NotNull private final TreeMap<Integer, Integer> myInvertedFragments1 = new TreeMap<Integer, Integer>();
    @NotNull private final TreeMap<Integer, Integer> myInvertedFragments2 = new TreeMap<Integer, Integer>();

    public void put1(int start, int newStart, int length) {
      myFragments1.put(start, newStart);
      myFragments1.put(start + length, -1);

      myInvertedFragments1.put(newStart, start);
      myInvertedFragments1.put(newStart + length, -1);
    }

    public void put2(int start, int newStart, int length) {
      myFragments2.put(start, newStart);
      myFragments2.put(start + length, -1);

      myInvertedFragments2.put(newStart, start);
      myInvertedFragments2.put(newStart + length, -1);
    }

    @NotNull
    public LineNumberConvertor build() {
      return new LineNumberConvertor(myFragments1, myFragments2, myInvertedFragments1, myInvertedFragments2);
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
    private final List<CorrectedChange> myChanges = new SmartList<CorrectedChange>();

    @SuppressWarnings("UnnecessaryLocalVariable")
    public void handleOnesideChange(int startLine, int endLine, int shift, @NotNull Side masterSide) {
      int oldOnesideStart = startLine;
      int oldTwosideStart = convert(startLine, masterSide, true, false);
      assert oldTwosideStart != -1;

      int oldLength = endLine - startLine;
      int newLength = oldLength + shift;

      myChanges.add(new CorrectedChange(oldOnesideStart, oldTwosideStart, oldLength, newLength, masterSide));
    }

    public int convertCorrected(int value, @NotNull Side side, boolean fromOneside, boolean approximate) {
      if (fromOneside) {
        return convertFromOneside(value, side, approximate, myChanges.size() - 1);
      }
      else {
        return convertFromTwoside(value, side, approximate, myChanges.size() - 1);
      }
    }

    private int convertFromTwoside(int value, @NotNull Side side, boolean approximate, int index) {
      if (index < 0) {
        return convert(getFragments(side, false), value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (change.side != side) { // ?u' -> ?o'
        int converted = convertFromTwoside(value, side, approximate, index - 1);
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
          return convertFromTwoside(value, side, approximate, index - 1);
        }
        if (value >= change.startTwoside + change.newLength) { // Em' -> Eo'
          // Em' == Em + shift; Eo' == Eo + shift
          // value == Em'; converted == Eo
          int converted = convertFromTwoside(value - shift, side, approximate, index - 1);
          return append(converted, shift);
        }

        // Mm' -> Mo'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // convertedStart == Ao; value - change.startOneside == Mm' - Am'
        int convertedStart = convertFromTwoside(change.startTwoside, side, approximate, index - 1);
        return append(convertedStart, value - change.startTwoside);
      }
    }

    private int convertFromOneside(int value, @NotNull Side side, boolean approximate, int index) {
      if (index < 0) {
        return convert(getFragments(side, true), value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (value < change.startOneside) { // So' -> Sm', So' -> Su'
        // So' == So; Sm' == Sm; Su' == Su
        // value = So'
        return convertFromOneside(value, side, approximate, index - 1);
      }
      if (value >= change.startOneside + change.newLength) { // Eo' -> Em', Eo' -> Eu'
        // Eo' == Eo + shift; Em' == Em + shift; Eu' == Eu
        // value = Eo'
        int converted = convertFromOneside(value - shift, side, approximate, index - 1);
        return append(converted, side == change.side ? shift : 0);
      }

      if (side != change.side) { // Mo' -> Mu'
        if (!approximate) return -1;
        // we can't convert Mo' into Mo. And thus get valid Mu/Mu'.
        // return: Au'
        return convertFromOneside(change.startOneside, side, approximate, index - 1);
      }
      else { // Mo' -> Mm'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // value = Mo'
        int convertedStart = convertFromOneside(change.startOneside, side, approximate, index - 1);
        return append(convertedStart, value - change.startOneside);
      }
    }

    private int append(int value, int shift) {
      return value == -1 ? -1 : value + shift;
    }
  }

  private static class CorrectedChange {
    public final int startOneside;
    public final int startTwoside;
    public final int oldLength;
    public final int newLength;
    @NotNull public final Side side;

    public CorrectedChange(int startOneside, int startTwoside, int oldLength, int newLength, @NotNull Side side) {
      this.startOneside = startOneside;
      this.startTwoside = startTwoside;
      this.oldLength = oldLength;
      this.newLength = newLength;
      this.side = side;
    }
  }
}
