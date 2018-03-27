import java.util.List;

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

  static void testTwoLengths(int[] data) {
    String s1 = data.length > 0 ? "foo" : null;
    String s2 = data.length == 0 ? null : "bar";
    if(s1 == null || <warning descr="Condition 's2 == null' is always 'false' when reached">s2 == null</warning>) {
      System.out.println("Test");
    }
  }

  static final String FOO = "bar";

  void testStaticLength() {
    if(<warning descr="Condition 'FOO.isEmpty()' is always 'false'">FOO.isEmpty()</warning>) System.out.println("nope");
  }

  void testWrongMerge(boolean a, boolean b, int c) {
    int currentLevel = 0;
    if (a) {
      currentLevel = c;
    }
    if(currentLevel > 0 && b || currentLevel < 0) {

    }
  }

  void testLength2(String name) {
    boolean completeDigits = name.length() > 1;
    for (int j = 1; j < name.length(); ++j) {
      <warning descr="Condition 'completeDigits' at the left side of assignment expression is always 'true'. Can be simplified">completeDigits</warning> &= Character.isDigit(name.charAt(j));
      if (!completeDigits) break;
    }
    if (completeDigits) name = "this";
    System.out.println(name);
  }

  void testLengthNonFlushed(String s) {
    if (s.length() > 10) {
      System.out.println(s);
      if (<warning descr="Condition 's.length() > 5' is always 'true'">s.length() > 5</warning>) {
        System.out.println(s);
      }
    }
  }

  void getMax(List<String> points, String source) {
    int min = Integer.MAX_VALUE;
    int nextSelectedIndex = -1;
    for (int i = points.size() - 1; i >= 0; i--) {
      final int distance = calcDistance(source, points.get(i));
      if (distance < min) {
        min = distance;
        nextSelectedIndex = i;
      }
    }
    if (min == Integer.MAX_VALUE) {
      return;
    }
    if (<warning descr="Condition 'nextSelectedIndex == -1' is always 'false'">nextSelectedIndex == -1</warning>) {
      System.out.println("Impossible");
    }
    System.out.println(nextSelectedIndex);
  }

  private int calcDistance(String s1, String s2) {
    return s1.length() - s2.length();
  }

  public void testBitwiseAnd() {
    int state = getState() & 0xF;
    switch (state) {
      <warning descr="Switch label 'case 24:' is unreachable">case 24:</warning>
        System.out.println("Impossible");
    }
  }

  public void testBitwiseAndOk() {
    if((getState() & 0x1) == 0x1) {
      System.out.println("ok");
    }
  }

  void testDouble(double d) {
    if(d > 0 && d < 1) {
      System.out.println("ok");
    }
  }

  private int getState() {
    return (int)(Math.random() * 100);
  }
}
