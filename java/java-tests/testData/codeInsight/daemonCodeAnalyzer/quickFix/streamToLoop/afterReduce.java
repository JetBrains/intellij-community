// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class Main {
  public static String testReduce2(List<String> list) {
      String acc = "";
      for (String s: list) {
          acc = acc + s;
      }
      return acc;
  }

  public static int testReduce3(List<String> list) {
      Integer acc = 0;
      for (String s: list) {
          acc = acc + s.length();
      }
      return acc;
  }

  private static Integer testReduceMethodRef(List<Integer> numbers) {
      Integer acc = 0;
      for (Integer number: numbers) {
          acc = Math.max(acc, number);
      }
      return acc;
  }

  private static OptionalInt testReduceOptionalInt() {
      boolean seen = false;
      int acc = 0;
      for (int i: new int[]{}) {
          if (!seen) {
              seen = true;
              acc = i;
          } else {
              acc = acc * i;
          }
      }
      return seen ? OptionalInt.of(acc) : OptionalInt.empty();
  }

  private static Optional<Integer> testReduceOptionalInteger(Integer... numbers) {
      boolean seen = false;
      Integer acc = null;
      for (Integer number: numbers) {
          if (!seen) {
              seen = true;
              acc = number;
          } else {
              acc = acc * number;
          }
      }
      return seen ? Optional.of(acc) : Optional.empty();
  }

  public static void main(String[] args) {
    System.out.println(testReduce2(asList("a", "b", "c")));
    System.out.println(testReduce3(asList("a", "b", "c")));
    testReduceMethodRef(asList(1, 10, 5));
    System.out.println(testReduceOptionalInt());
    System.out.println(testReduceOptionalInteger(1,2,3,4));
  }
}