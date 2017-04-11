import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

public class LongRangeKnownMethods {
  void testIndexOf(String s) {
    int idx = s.indexOf("xyz");
    if(idx >= 0) {
      System.out.println("Found");
    } else if(<warning descr="Condition 'idx == -1' is always 'true'">idx == -1</warning>) {
      System.out.println("Not found");
    }
  }

  void testLocalDateTime(LocalDateTime ldt) {
    if(<warning descr="Condition 'ldt.getHour() == 24' is always 'false'">ldt.getHour() == 24</warning>) System.out.println(1);
    if(<warning descr="Condition 'ldt.getMinute() >= 0' is always 'true'">ldt.getMinute() >= 0</warning>) System.out.println(2);
    if(<warning descr="Condition 'ldt.getSecond() >= 60' is always 'false'">ldt.getSecond() >= 60</warning>) System.out.println(3);
  }

  private static int twiceIndexOf(String text, int start, int end) {
    int paragraphStart = text.lastIndexOf("\n\n", start);
    int paragraphEnd = text.indexOf("\n\n", end);
    if (paragraphStart >= paragraphEnd) {
      return text.length();
    }
    return (paragraphStart >= 0 ? paragraphStart + 2 : 0) +
           (<warning descr="Condition 'paragraphEnd < 0' is always 'false' when reached">paragraphEnd < 0</warning> ? text.length() : paragraphEnd);
  }

  void test(String s) {
    if (<warning descr="Condition 's.isEmpty() && s.length() > 2' is always 'false'">s.isEmpty() && <warning descr="Condition 's.length() > 2' is always 'false' when reached">s.length() > 2</warning></warning>) {
      System.out.println("Never");
    }
  }

  void test2(String s) {
    if (s.isEmpty() && <warning descr="Condition 's.length() == 0' is always 'true' when reached">s.length() == 0</warning>) {
      System.out.println("Ok");
    }
  }

  void test3(String s) {
    if (<warning descr="Condition 's.startsWith(\"xyz\") && s.length() < 3' is always 'false'">s.startsWith("xyz") && <warning descr="Condition 's.length() < 3' is always 'false' when reached">s.length() < 3</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testEmpty(String s) {
    if (<warning descr="Condition 's.isEmpty() && s.startsWith(\"xyz\")' is always 'false'">s.isEmpty() && <warning descr="Condition 's.startsWith(\"xyz\")' is always 'false' when reached">s.startsWith("xyz")</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testOk(String s) {
    if (s.length() <= 3 && s.endsWith("xyz")) {
      System.out.println("Possible");
    }
  }

  void testInterfere(String s, String s2) {
    if (!s.isEmpty() && s2.startsWith(s)) {
      if(<warning descr="Condition 's2.isEmpty()' is always 'false'">s2.isEmpty()</warning>) {
        System.out.println();
      }
    }
  }

  void test4() {
    String s = "abcd";
    if (<warning descr="Condition 's.startsWith(\"efg\")' is always 'false'">s.startsWith("efg")</warning>) {
      System.out.println("Impossible");
    }
  }

  void testEquals(boolean b, boolean c) {
    String s1 = b ? "x" : "y";
    String s2 = c ? "x" : "b";
    if(s1.equals(s2) && <warning descr="Condition 'b' is always 'true' when reached">b</warning>) {
      System.out.println("B is always true");
    }
  }

  void testEqualsIgnoreCase(String s) {
    if(<warning descr="Condition 's.equalsIgnoreCase(\"xyz\") && s.isEmpty()' is always 'false'">s.equalsIgnoreCase("xyz") && <warning descr="Condition 's.isEmpty()' is always 'false' when reached">s.isEmpty()</warning></warning>) {
      System.out.println("Never");
    }
  }

  void testIndexOfUpperBound(String s) {
    int idx = "abcdefgh".indexOf(s);
    if(<warning descr="Condition 'idx > 8' is always 'false'">idx > 8</warning>) {
      System.out.println("Impossible");
    }
  }

  void testMax(int x) {
    x = Math.max(x, 0);
    if (<warning descr="Condition 'x > -1' is always 'true'">x > -1</warning>) {
      System.out.println("Always");
    }
    if (x > 0) {
      System.out.println("Not always");
    }
  }

  void testMin(long x, long y) {
    if (x < 10 && y > 10) {
      y = Long.min(x, y);
      if (<warning descr="Condition 'y > 20' is always 'false'">y > 20</warning>) {
        System.out.println("Impossible");
      }
    }
    if (y > 20) {
      System.out.println("Possible");
    }
  }

  void testMinMax(List<String> rows) {
    int start = Integer.MAX_VALUE;
    int end = -1;

    for (int i = 0; i < rows.size(); i++) {
      String row = rows.get(i);
      if (!row.isEmpty()) {
        start = Math.min(start, i);
        end = Math.max(end, i);
      }
    }

    if(end >= 0 && <warning descr="Condition 'start < Integer.MAX_VALUE' is always 'true' when reached">start < Integer.MAX_VALUE</warning>) {
      System.out.println("Ok");
    }
  }

  void testAbs(long x, int y) {
    x = Math.abs(x);
    y = Math.abs(y);
    if (x == Long.MIN_VALUE) {
      System.out.println("possible");
    }
    if (<warning descr="Condition 'x == Long.MIN_VALUE + 1' is always 'false'">x == Long.MIN_VALUE + 1</warning>) {
      System.out.println("impossible");
    }
    if (<warning descr="Condition 'x == Integer.MIN_VALUE' is always 'false'">x == Integer.MIN_VALUE</warning>) {
      System.out.println("impossible");
    }
    if (y == Integer.MIN_VALUE) {
      System.out.println("possible");
    } else if(<warning descr="Condition 'y < 0' is always 'false'">y < 0</warning>) {
      System.out.println("impossible");
    }
  }

  void testStringComparison(String name) {
    // Parentheses misplaced -- found in AndroidStudio
    if (!(name.equals("layout_width") && <warning descr="Condition '!(name.equals(\"layout_height\"))' is always 'true'">!(name.equals("layout_height"))</warning> &&
          <warning descr="Condition '!(name.equals(\"id\"))' is always 'true'">!(name.equals("id"))</warning>)) {
      System.out.println("ok");
    }
  }

  void testFlush(MyReader r) {
    if(r.getValue().equals("abc")) {
      r.readNext();
      if(r.getValue().equals("abcd")) {
        System.out.println("ok");
      }
    }
  }

  void testNoFlush(MyReader r) {
    if(r.getValue().equals("abc")) {
      if(<warning descr="Condition 'r.getValue().equals(\"abcd\")' is always 'false'">r.getValue().equals("abcd")</warning>) {
        System.out.println("ok");
      }
    }
  }

  static class MyReader {
    private String value = "";

    final String getValue() {
      return value;
    }

    void readNext() {
      value = new Scanner(System.in).next();
    }
  }
}
