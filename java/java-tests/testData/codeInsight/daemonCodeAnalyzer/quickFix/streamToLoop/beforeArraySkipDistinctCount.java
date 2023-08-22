// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;

public class Main {
  private static long test() {
    return Arrays.stream(new Integer[] {1,2,3,2,3}).skip(1).distinct().cou<caret>nt();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}