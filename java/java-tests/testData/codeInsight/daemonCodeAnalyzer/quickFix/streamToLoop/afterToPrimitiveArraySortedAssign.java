// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public int[] testPrimitive(List<String> list) {
      List<String> toSort = new ArrayList<>();
      for (String s : list) {
          toSort.add(s);
      }
      toSort.sort(null);
      int[] ints = new int[10];
      int count = 0;
      for (String s : toSort) {
          int length = s.length();
          if (ints.length == count) ints = Arrays.copyOf(ints, count * 2);
          ints[count++] = length;
      }
      ints = Arrays.copyOfRange(ints, 0, count);
      return ints;
  }

  public static void main(String[] args) {
    System.out.println(Arrays
                         .toString(new Main().testPrimitive(asList("sdfg", "asd", "sdgfdsg", "adas", "asgfsgf", "werrew"))));
  }
}