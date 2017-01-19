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
  // Master -> Slave
  @NotNull private final TreeMap<Integer, Integer> myFragments;

  // Slave -> Master
  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments;

  @NotNull private final Corrector myCorrector = new Corrector();

  private LineNumberConvertor(@NotNull TreeMap<Integer, Integer> fragments,
                              @NotNull TreeMap<Integer, Integer> invertedFragments) {
    myFragments = fragments;
    myInvertedFragments = invertedFragments;
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

  public int convert(int value, boolean fromMaster, boolean approximate) {
    return myCorrector.convertCorrected(value, fromMaster, approximate);
  }

  /**
   * Master was changed. We should update converters, because of changed shift.
   *
   * @param synchronous true - slave were changed in a same way
   *                    false - slave was not changed
   */
  public void handleMasterChange(int startLine, int endLine, int shift, boolean synchronous) {
    myCorrector.handleMasterChange(startLine, endLine, shift, synchronous);
  }


  /**
   * @param approximate false: return exact matching between lines, and -1 if it's impossible
   *                    true: return 'good enough' position, even if exact matching is impossible
   */
  private int convertRaw(boolean fromMaster, int value, boolean approximate) {
    TreeMap<Integer, Integer> fragments = fromMaster ? myFragments : myInvertedFragments;

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
    @NotNull private final TreeMap<Integer, Integer> myFragments = new TreeMap<>();
    @NotNull private final TreeMap<Integer, Integer> myInvertedFragments = new TreeMap<>();

    public void put(int start, int newStart, int length) {
      myFragments.put(start, newStart);
      myFragments.put(start + length, -1);

      myInvertedFragments.put(newStart, start);
      myInvertedFragments.put(newStart + length, -1);
    }

    @NotNull
    public LineNumberConvertor build() {
      return new LineNumberConvertor(myFragments, myInvertedFragments);
    }
  }

  /*
   * myFragments allow to convert between Sm-So-Su, Mm-Mo-Mu, Em-Eo-Eu.
   *
   * Corrector processes information about master side modifications B -> B'
   * and allows to convert between Sm'-So'-Su', Mm'-Mo'-Mu', Em'-Eo'-Eu'.
   *
   * sync - when master and slave are modified in the same way
   * async - when only master is modified
   *
   *
   *         Before                            After
   *
   *  sync            async
   *         master
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
   * change.startMaster == Ao == Ao'
   * change.startSlave == Am == Am'
   *
   * change.oldLength == Bo - Ao
   * change.newLength == Bo' - Ao'
   *
   * In case of multiple changes - we process them in reverse order (from new to old).
   *
   */
  private class Corrector {
    private final List<CorrectedChange> myChanges = new SmartList<>();

    @SuppressWarnings("UnnecessaryLocalVariable")
    public void handleMasterChange(int startLine, int endLine, int shift, boolean synchronous) {
      int oldLength = endLine - startLine;
      int newLength = oldLength + shift;

      if (synchronous) {
        int oldSlaveStart = convert(startLine, true, false);
        assert oldSlaveStart != -1;

        myChanges.add(new CorrectedChange(startLine, oldSlaveStart, oldLength, newLength));
      }
      else {
        myChanges.add(new CorrectedChange(startLine, oldLength, newLength));
      }
    }

    public int convertCorrected(int value, boolean fromMaster, boolean approximate) {
      if (fromMaster) {
        return convertFromMaster(value, approximate, myChanges.size() - 1);
      }
      else {
        return convertFromSlave(value, approximate, myChanges.size() - 1);
      }
    }

    private int convertFromSlave(int value, boolean approximate, int index) {
      if (index < 0) {
        return convertRaw(false, value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (!change.synchronous) { // ?u' -> ?o'
        int converted = convertFromSlave(value, approximate, index - 1);
        if (converted < change.startMaster) { // Su' -> So'
          // Su' == Su; So' == So
          // value == Su'; converted == So
          return converted;
        }
        if (converted >= change.startMaster + change.oldLength) { // Eu' -> Eo'
          // Eo' == Eo + shift; Eu' == Eu
          // value == Eu'; converted == Eo
          return converted + shift;
        }

        // Mu' -> Mo'
        if (!approximate) return -1;
        // We can't convert Mo into Mo'
        // converted == Mo
        return append(converted, Math.min(change.newLength, converted - change.startMaster));
      }
      else { // ?m '-> ?o'
        if (value < change.startSlave) { // Sm' -> So'
          return convertFromSlave(value, approximate, index - 1);
        }
        if (value >= change.startSlave + change.newLength) { // Em' -> Eo'
          // Em' == Em + shift; Eo' == Eo + shift
          // value == Em'; converted == Eo
          int converted = convertFromSlave(value - shift, approximate, index - 1);
          return append(converted, shift);
        }

        // Mm' -> Mo'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // convertedStart == Ao; value - change.startMaster == Mm' - Am'
        int convertedStart = convertFromSlave(change.startSlave, approximate, index - 1);
        return append(convertedStart, value - change.startSlave);
      }
    }

    private int convertFromMaster(int value, boolean approximate, int index) {
      if (index < 0) {
        return convertRaw(true, value, approximate);
      }

      CorrectedChange change = myChanges.get(index);
      int shift = change.newLength - change.oldLength;

      if (value < change.startMaster) { // So' -> Sm', So' -> Su'
        // So' == So; Sm' == Sm; Su' == Su
        // value = So'
        return convertFromMaster(value, approximate, index - 1);
      }
      if (value >= change.startMaster + change.newLength) { // Eo' -> Em', Eo' -> Eu'
        // Eo' == Eo + shift; Em' == Em + shift; Eu' == Eu
        // value = Eo'
        int converted = convertFromMaster(value - shift, approximate, index - 1);
        return append(converted, change.synchronous ? shift : 0);
      }

      if (!change.synchronous) { // Mo' -> Mu'
        if (!approximate) return -1;
        // we can't convert Mo' into Mo. And thus get valid Mu/Mu'.
        // return: Au'
        return convertFromMaster(change.startMaster, approximate, index - 1);
      }
      else { // Mo' -> Mm'
        // Ao == Ao'; Am == Am'; Mo' - Ao' == Mm' - Am'
        // value = Mo'
        int convertedStart = convertFromMaster(change.startMaster, approximate, index - 1);
        return append(convertedStart, value - change.startMaster);
      }
    }

    private int append(int value, int shift) {
      return value == -1 ? -1 : value + shift;
    }
  }

  private static class CorrectedChange {
    public final boolean synchronous;

    public final int startMaster;
    public final int startSlave;
    public final int oldLength;
    public final int newLength;

    public CorrectedChange(int startMaster, int oldLength, int newLength) {
      this.synchronous = false;
      this.startSlave = -1;

      this.startMaster = startMaster;
      this.oldLength = oldLength;
      this.newLength = newLength;
    }

    public CorrectedChange(int startMaster, int startSlave, int oldLength, int newLength) {
      this.synchronous = true;

      this.startMaster = startMaster;
      this.startSlave = startSlave;
      this.oldLength = oldLength;
      this.newLength = newLength;
    }
  }
}
