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

import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

public class LineNumberConvertor {
  @NotNull private final TreeMap<Integer, Integer> myFragments1;
  @NotNull private final TreeMap<Integer, Integer> myFragments2;

  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments1;
  @NotNull private final TreeMap<Integer, Integer> myInvertedFragments2;

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
    return convert(myFragments1, value);
  }

  public int convert2(int value) {
    return convert(myFragments2, value);
  }

  public int convertInv1(int value) {
    return convert(myInvertedFragments1, value);
  }

  public int convertInv2(int value) {
    return convert(myInvertedFragments2, value);
  }

  public int convertApproximate1(int value) {
    return convertApproximate(myFragments1, value);
  }

  public int convertApproximate2(int value) {
    return convertApproximate(myFragments2, value);
  }

  public int convertApproximateInv1(int value) {
    return convertApproximate(myInvertedFragments1, value);
  }

  public int convertApproximateInv2(int value) {
    return convertApproximate(myInvertedFragments2, value);
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

  /*
   * This convertor returns exact matching between lines, and -1 if it's impossible (line is folded)
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

    @NotNull
    public static LineNumberConvertor createLeft(int lines) {
      Builder builder = new Builder();
      builder.put1(0, 0, lines);
      return builder.build();
    }

    @NotNull
    public static LineNumberConvertor createRight(int lines) {
      Builder builder = new Builder();
      builder.put2(0, 0, lines);
      return builder.build();
    }
  }
}
