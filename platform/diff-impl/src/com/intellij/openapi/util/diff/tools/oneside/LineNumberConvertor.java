package com.intellij.openapi.util.diff.tools.oneside;

import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

class LineNumberConvertor {
  @NotNull
  public static TIntFunction ID = new TIntFunction() {
    @Override
    public int execute(int value) {
      return value;
    }
  };

  @NotNull private final TIntFunction myConvertor1;
  @NotNull private final TIntFunction myConvertor2;

  public LineNumberConvertor(@NotNull TIntFunction convertor1, @NotNull TIntFunction convertor2) {
    myConvertor1 = convertor1;
    myConvertor2 = convertor2;
  }

  @NotNull
  public TIntFunction getConvertor1() {
    return myConvertor1;
  }

  @NotNull
  public TIntFunction getConvertor2() {
    return myConvertor2;
  }

  public static class Builder {
    private final TreeMap<Integer, Integer> myFragments1 = new TreeMap<Integer, Integer>();
    private final TreeMap<Integer, Integer> myFragments2 = new TreeMap<Integer, Integer>();

    private final TreeMap<Integer, Integer> myInvertedFragments1 = new TreeMap<Integer, Integer>();
    private final TreeMap<Integer, Integer> myInvertedFragments2 = new TreeMap<Integer, Integer>();

    public void put1(int start, int end, int newStart) {
      myFragments1.put(start, newStart);
      myFragments1.put(end, -1);

      myInvertedFragments1.put(newStart, start);
      myInvertedFragments1.put(newStart + end - start, end);
    }

    public void put2(int start, int end, int newStart) {
      myFragments2.put(start, newStart);
      myFragments2.put(end, -1);

      myInvertedFragments2.put(newStart, start);
      myInvertedFragments2.put(newStart + end - start, end);
    }

    @NotNull
    public LineNumberConvertor build() {
      return new LineNumberConvertor(getConvertor(myFragments1), getConvertor(myFragments2));
    }

    @NotNull
    public LineNumberConvertor buildInverted() {
      return new LineNumberConvertor(getInvertedConvertor(myInvertedFragments1), getInvertedConvertor(myInvertedFragments2));
    }

    @NotNull
    private static TIntFunction getConvertor(@NotNull final TreeMap<Integer, Integer> fragments) {
      return new TIntFunction() {
        @Override
        public int execute(int value) {
          Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
          if (floor == null || floor.getValue() == -1) return -1;
          return floor.getValue() - floor.getKey() + value;
        }
      };
    }

    @NotNull
    private static TIntFunction getInvertedConvertor(@NotNull final TreeMap<Integer, Integer> fragments) {
      return new TIntFunction() {
        @Override
        public int execute(int value) {
          Map.Entry<Integer, Integer> floor = fragments.floorEntry(value);
          if (floor == null) return 0;
          return floor.getValue() - floor.getKey() + value;
        }
      };
    }

    @NotNull
    public static LineNumberConvertor createLeft(int lines) {
      Builder builder = new Builder();
      builder.put1(0, lines, 0);
      return builder.build();
    }

    @NotNull
    public static LineNumberConvertor createRight(int lines) {
      Builder builder = new Builder();
      builder.put2(0, lines, 0);
      return builder.build();
    }

    @NotNull
    public static LineNumberConvertor createInvertedLeft(int lines) {
      Builder builder = new Builder();
      builder.put1(0, lines, 0);
      return builder.buildInverted();
    }

    @NotNull
    public static LineNumberConvertor createInvertedRight(int lines) {
      Builder builder = new Builder();
      builder.put2(0, lines, 0);
      return builder.buildInverted();
    }
  }
}
