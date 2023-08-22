// "Replace Stream API chain with loop" "true-preview"

import java.util.*;

public class Main {
  private static long test(List<?> list) {
    return list.stream().skip(list.size()/2).distinct().cou<caret>nt();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(1,2,3,3,2,1,1,2,3)));
  }
}