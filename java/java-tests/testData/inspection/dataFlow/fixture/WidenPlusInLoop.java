import java.util.*;

public class WidenPlusInLoop {
  private static void notComplex(int[] arr) {
    for (int j = 0; j < arr.length; j++) {
      int block = arr[j];
      if (block != 0) {
        int index = j << 5;
        for (int i = 31; i >= 0; i--) {
          if ((block & 1) != 0) {
            System.out.println("foo");
          }
          index++;
          block >>>= 1;
        }
      }
    }
  }

  void test1() {
    for (int i = 0; <warning descr="Condition 'i < Integer.MAX_VALUE' is always 'true'">i < Integer.MAX_VALUE</warning>; i += 2) {
      System.out.println(i);
    }
  }

  void test2() {
    for (int i = 0; i < Integer.MAX_VALUE; i += 3) {
      System.out.println(i);
    }
  }

  void test3(int[] arr) {
    for (int i=0; i<arr.length; i+=4) {
      System.out.println(arr[i]);
      System.out.println(arr[i + 1]);
      System.out.println(arr[i + 2]);
      System.out.println(arr[i + 3]);
      if (<warning descr="Condition 'i % 8 == 2' is always 'false'">i % 8 == 2</warning>) {

      }
      if (i % 8 == 4) {

      }
    }
  }

  void test4() {
    for (int i = 1; i < Integer.MAX_VALUE; i += 2) {
      System.out.println(i);
    }
  }

  void test5() {
    for (int i = 1; <warning descr="Condition 'i < Integer.MAX_VALUE' is always 'true'">i < Integer.MAX_VALUE</warning>; i += 4) {
      System.out.println(i);
    }
  }

  void test6() {
    for (int i = 2; <warning descr="Condition 'i < Integer.MAX_VALUE' is always 'true'">i < Integer.MAX_VALUE</warning>; i += 4) {
      System.out.println(i);
    }
  }

  void test7() {
    for (int i = 3; i < Integer.MAX_VALUE; i += 4) {
      System.out.println(i);
    }
  }
}
