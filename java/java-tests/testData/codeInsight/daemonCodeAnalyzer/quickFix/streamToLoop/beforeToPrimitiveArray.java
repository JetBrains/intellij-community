// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.IntStream;

public class Main {
  private static int[] test() {
    return IntStream.range(-100, 100).filter(x -> x > 0).toArr<caret>ay();
  }

  public int[] testPrimitive(List<String> list) {
    int[] ints = list.stream().sorted().mapToInt(String::length).toArray();
    return ints;
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(test()));
    System.out.println(Arrays.toString(new Main().testPrimitive(asList("sdfg", "asd", "sdgfdsg", "adas", "asgfsgf", "werrew"))));
  }
}