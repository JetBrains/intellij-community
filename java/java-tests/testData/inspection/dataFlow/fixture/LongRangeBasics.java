public class LongRangeBasics {
  void testSwitch(int i) {
    switch (i) {
      case 0:
        System.out.println("0");
        break;
      case 1:
        System.out.println("1");
        return;
      case 2:
        System.out.println("2");
        return;
      default:
        System.out.println("default");
        break;
    }
    if(i == 0) {
      System.out.println("ouch");
    }
    // i > 0 and i < 3 means (i == 1 || i == 2); in both cases we already returned
    if(<warning descr="Condition 'i > 0 && i < 3' is always 'false'">i > 0 && <warning descr="Condition 'i < 3' is always 'false' when reached">i < 3</warning></warning>) {
      System.out.println("oops");
    }
  }

  void test(int i) {
    if(i > 5) {
      if(<warning descr="Condition 'i < 0' is always 'false'">i < 0</warning>) {
        System.out.println("Hello");
      }
    }
  }

  void test2(char c) {
    int i = c;
    if(<warning descr="Condition 'i > 0x10000' is always 'false'">i > 0x10000</warning>) {
      System.out.println("Hello");
    }
  }

  void test3(String s) {
    int i = s.charAt(0);
    if(<warning descr="Condition 'i > 0x10000' is always 'false'">i > 0x10000</warning>) {
      System.out.println("Hello");
    }
  }

  void test4(String s) {
    if(<warning descr="Condition 's.charAt(0) < 0x10000' is always 'true'">s.charAt(0) < 0x10000</warning>) {
      System.out.println("Hello");
    }
  }

  void test1(int i, int j) {
    if(i > 0 && j > i) {
      // j > i which is > 0 means that j >= 2
      if(<warning descr="Condition 'j == 1' is always 'false'">j == 1</warning>) {
        if(i < 0) {
          System.out.println("oops");
        }
      }
    }
  }

  void testLength(String s) {
    if(s.length() < 2) {
      if(<warning descr="Condition 's.length() > 4' is always 'false'">s.length() > 4</warning>) {
        System.out.println("Never");
      }
      if(s.length() == 1) {
        System.out.println("One");
      } else if(<warning descr="Condition 's.length() == 0' is always 'true'">s.length() == 0</warning>) {
        System.out.println("Empty");
      }
    }
    if(<warning descr="Condition 's.length() < 0' is always 'false'">s.length() < 0</warning>) {
      System.out.println("Never");
    }
  }

  void testArrayLength(int[] arr) {
    if (arr.length > 0) {
      System.out.println("Ok");
    } else if(<warning descr="Condition 'arr.length == 0' is always 'true'">arr.length == 0</warning>) {
      System.out.println("Empty");
    }
    if (<warning descr="Condition 'arr.length < 0' is always 'false'">arr.length < 0</warning>) {
      System.out.println("Impossible");
    }
  }
}
