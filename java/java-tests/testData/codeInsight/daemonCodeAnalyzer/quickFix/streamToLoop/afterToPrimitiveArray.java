// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.IntStream;

public class Main {
  private static int[] test() {
      int[] arr = new int[10];
      int count = 0;
      for (int x = -100; x < 100; x++) {
          if (x > 0) {
              if (arr.length == count) arr = Arrays.copyOf(arr, count * 2);
              arr[count++] = x;
          }
      }
      arr = Arrays.copyOfRange(arr, 0, count);
      return arr;
  }

  public int[] testPrimitive(List<String> list) {
      List<String> toSort = new ArrayList<>();
      for (String s: list) {
          toSort.add(s);
      }
      toSort.sort(null);
      int[] ints = new int[10];
      int count = 0;
      for (String s: toSort) {
          int length = s.length();
          if (ints.length == count) ints = Arrays.copyOf(ints, count * 2);
          ints[count++] = length;
      }
      ints = Arrays.copyOfRange(ints, 0, count);
      return ints;
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(test()));
    System.out.println(Arrays.toString(new Main().testPrimitive(asList("sdfg", "asd", "sdgfdsg", "adas", "asgfsgf", "werrew"))));
  }
}