// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public int[] testPrimitive(List<String> list) {
    int[] ints = list.stream().sorted().mapToInt(String::length).<caret>toArray();
    return ints;
  }

  public static void main(String[] args) {
    System.out.println(Arrays
                         .toString(new Main().testPrimitive(asList("sdfg", "asd", "sdgfdsg", "adas", "asgfsgf", "werrew"))));
  }
}