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

  // IDEA-229598
  private static int[] test(int countSections) {
    int[] sections = new int[countSections];

    if (countSections == 1) {
      sections[0] = 0;
    } else if (countSections > 1) {
      if (countSections % 2 == 0) {
        int internalId = -1 * (countSections - 1);

        for (int i = 0; i < countSections; i++) {
          if (internalId == 1) {
            internalId++;
          }
          sections[i] = Math.abs(internalId);
          internalId += 2;
        }
      }
    }

    return sections;
  }

  void infiniteLoop() {
    for (int i = 1; <warning descr="Condition 'i != 10' is always 'true'">i != 10</warning>; i += 2) {
      // ...
    }
  }
  
  void nestedLoop() {
    long value = 1;
    for (int a = 0; a < 2; a++) {
      for (int b = 0; b < 2; b++) {
        b++;
      }
      if (<warning descr="Condition 'a >= 0' is always 'true'">a >= 0</warning>) {}
      value <<= 2 * a;
    }
  }
}
