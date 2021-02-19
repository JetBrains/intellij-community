import java.util.*;
import java.io.*;

public class LongRangeKnownMethods {
  void testIndexOf(String s) {
    int idx = s.indexOf("xyz");
    if(idx >= 0) {
      System.out.println("Found");
    } else if(<warning descr="Condition 'idx == -1' is always 'true'">idx == -1</warning>) {
      System.out.println("Not found");
    }
  }

  void testIndexOf2(String s) {
    int idx = s.indexOf("foo")+3;
    if(<warning descr="Condition 'idx == -1' is always 'false'">idx == -1</warning>) {
      System.out.println("Bug");
    }
  }

  void testExternalAnnotations(int i, long l) {
    if(<warning descr="Condition 'Integer.bitCount(i) == -1' is always 'false'">Integer.bitCount(i) == -1</warning>) {
      System.out.println("Impossible");
    }
    if(Long.<error descr="Cannot resolve method 'numberOfLeadingZeroes' in 'Long'">numberOfLeadingZeroes</error>(l) <= 64) {
      System.out.println("Always");
    }
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
    if (<warning descr="Condition 's.isEmpty() && s.length() > 2' is always 'false'">s.isEmpty() && <warning descr="Condition 's.length() > 2' is always 'false' when reached"><warning descr="Result of 's.length()' is always '0'">s.length()</warning> > 2</warning></warning>) {
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
      y = Long.<warning descr="Result of min is the same as the first argument making the call meaningless">min</warning>(x, y);
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
    if (!(name.equals("layout_width") && <warning descr="Condition '!(name.equals(\"layout_height\"))' is always 'true'">!(<warning descr="Result of 'name.equals(\"layout_height\")' is always 'false'">name.equals("layout_height")</warning>)</warning> &&
          <warning descr="Condition '!(name.equals(\"id\"))' is always 'true'">!(<warning descr="Result of 'name.equals(\"id\")' is always 'false'">name.equals("id")</warning>)</warning>)) {
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

  void testEmptyList(List<String> list) {
    if (<warning descr="Condition 'list.get(0).isEmpty() && list.isEmpty()' is always 'false'">list.get(0).isEmpty() && <warning descr="Condition 'list.isEmpty()' is always 'false' when reached">list.isEmpty()</warning></warning>) {
      System.out.println("impossible");
    }
  }

  void testEmptyListGet(List<String> list) {
    if (list.isEmpty()) {
      System.out.println(list.<warning descr="The call to 'get' always fails as index is out of bounds">get</warning>(0));
    }
  }

  void testBoundError(List<String> list) {
    if (list.size() < 10) {
      System.out.println(list.<warning descr="The call to 'get' always fails as index is out of bounds">get</warning>(10));
    }
  }

  void testCollectionArray(Set<Object> currentElements, Object[] elements) {
    if(!currentElements.isEmpty() && elements.length == currentElements.size()) {
      if (<warning descr="Condition 'elements.length > 0' is always 'true'">elements.length > 0</warning>) {
        System.out.println("Yes");
      }
    }
  }

  public String getOrThrow(int index, List<String> localVariables) {
    if (index < localVariables.size()) {
      return localVariables.get(index);
    }
    else if (<warning descr="Condition 'index < 0' is always 'false'">index < 0</warning>) {
      throw new IndexOutOfBoundsException();
    }
    else return "";
  }

  void testSetFirst(TreeSet<Integer> set) {
    if (set.first() == 0 && <warning descr="Condition 'set.size() > 0' is always 'true' when reached">set.size() > 0</warning>) {
      System.out.println("Impossible");
    }
  }

  void testMap(HashMap<String, Integer> map) {
    if(<warning descr="Condition 'map.isEmpty() && map.containsKey(\"xyz\")' is always 'false'">map.isEmpty() && <warning descr="Condition 'map.containsKey(\"xyz\")' is always 'false' when reached">map.containsKey("xyz")</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testMapContainsValue(TreeMap<String, Integer> map) {
    if(<warning descr="Condition 'map.containsValue(1) && map.size() < 1' is always 'false'">map.containsValue(1) && <warning descr="Condition 'map.size() < 1' is always 'false' when reached">map.size() < 1</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testMapEquals(Map<String, String> map, Map<String, String> otherMap) {
    if(<warning descr="Condition 'map.isEmpty() && otherMap.equals(map) && otherMap.containsValue(\"xyz\")' is always 'false'">map.isEmpty() && otherMap.equals(map) && <warning descr="Condition 'otherMap.containsValue(\"xyz\")' is always 'false'">otherMap.containsValue("xyz")</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testListIndexOf(List<String> list) {
    if(<warning descr="Condition 'list.size() == 10 && list.indexOf(\"xyz\") == 15' is always 'false'">list.size() == 10 && <warning descr="Condition 'list.indexOf(\"xyz\") == 15' is always 'false' when reached">list.indexOf("xyz") == 15</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testGetUnknown(List<String> list, int index) {
    if(<warning descr="Condition 'list.get(index).isEmpty() && list.isEmpty()' is always 'false'">list.get(index).isEmpty() && <warning descr="Condition 'list.isEmpty()' is always 'false' when reached">list.isEmpty()</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  native void unknown();

  void testNewList() {
    List<String> list = new ArrayList<>();
    if(<warning descr="Condition 'list.isEmpty()' is always 'true'">list.isEmpty()</warning>) {
      System.out.println("Always");
    }
    unknown();
    if(<warning descr="Condition 'list.isEmpty()' is always 'true'">list.isEmpty()</warning>) {
      System.out.println("Still always");
    }
    testListIndexOf(list);
    if(list.isEmpty()) {
      System.out.println("Not sure anymore");
    }
  }

  void testDoubleBrace() {
    List<String> list = new ArrayList<String>() {{
      this.add("foo");
      this.add("bar");
    }};
    if(list.isEmpty()) {
      System.out.println("Not known now");
    }
  }

  void testSizeCheck() {
    List<String> list = new ArrayList<>();
    list.add(null);
    if(<warning descr="Condition 'list.size() == 0' is always 'false'">list.size() == 0</warning>) return;
    if(<warning descr="Condition 'list.size() == 0' is always 'false'">list.size() == 0</warning>) return;
  }
  
  native void unknown(List<String> list);

  void testSizeCheck2() {
    List<String> list = new ArrayList<>();
    unknown(list);
    if(list.size() == 0) return;
    if(<warning descr="Condition 'list.size() == 0' is always 'false'">list.size() == 0</warning>) return;
  }

  void testStartsWithEmpty(String s1, String s2) {
    if(s1.startsWith(s2) || <warning descr="Condition 's2.isEmpty()' is always 'false' when reached">s2.isEmpty()</warning>) {
      System.out.println("Oops");
    }
  }
  
  void testCalendar(Calendar c) {
    int month = c.get(Calendar.MONTH);
    if (<warning descr="Condition 'month < 0' is always 'false'">month < 0</warning>) {}
  }
  
  void testSkip(InputStream is, int amount) throws IOException {
    long skipped = is.skip(amount);
    if (<warning descr="Condition 'skipped > Integer.MAX_VALUE' is always 'false'">skipped > Integer.MAX_VALUE</warning>) {}
    if (<warning descr="Condition 'skipped < 0' is always 'false'">skipped < 0</warning>) {}
    if (<warning descr="Condition 'is.skip(-1) == 0' is always 'true'">is.skip(-1) == 0</warning>) {}
  }
  
  void testNumberToString(int i, long j) {
    String s;
    s = Integer.toHexString(i);
    if (<warning descr="Condition 's.length() < 1' is always 'false'">s.length() < 1</warning>) {}
    if (s.length() < 2) {}
    if (s.length() > 7) {}
    if (<warning descr="Condition 's.length() > 8' is always 'false'">s.length() > 8</warning>) {}
    s = Integer.toOctalString(i);
    if (s.length() > 10) {}
    if (<warning descr="Condition 's.length() > 11' is always 'false'">s.length() > 11</warning>) {}
    s = Integer.toBinaryString(i);
    if (s.length() > 31) {}
    if (<warning descr="Condition 's.length() > 32' is always 'false'">s.length() > 32</warning>) {}
    s = Long.toHexString(j);
    if (<warning descr="Condition 's.length() < 1' is always 'false'">s.length() < 1</warning>) {}
    if (s.length() < 2) {}
    if (s.length() > 15) {}
    if (<warning descr="Condition 's.length() > 16' is always 'false'">s.length() > 16</warning>) {}
    s = Long.toOctalString(j);
    if (s.length() > 21) {}
    if (<warning descr="Condition 's.length() > 22' is always 'false'">s.length() > 22</warning>) {}
    s = Long.toBinaryString(j);
    if (s.length() > 63) {}
    if (<warning descr="Condition 's.length() > 64' is always 'false'">s.length() > 64</warning>) {}
    if (i >= 0 && i < 100) {
      s = Integer.toBinaryString(i);
      if (s.length() > 6) {}
      if (<warning descr="Condition 's.length() > 7' is always 'false'">s.length() > 7</warning>) {}
    }
  }

  void testChainCall() {
    if (<warning descr="Condition 'getByte(0).intValue() == 256' is always 'false'">getByte(0).intValue() == 256</warning>) {

    }
  }

  native Byte getByte(int x);

  void testNumberToStringExact(boolean b) {
    int i = b ? 123 : 456;
    String s = Integer.toString(i);
    if (<warning descr="Condition 's.equals(\"123\") || s.equals(\"456\")' is always 'true'">s.equals("123") || <warning descr="Condition 's.equals(\"456\")' is always 'true' when reached">s.equals("456")</warning></warning>) {}
    // If we don't use 'b' at this point, 123 & 456 are joined into single LongRangeSet and constant evaluation for 's' doesn't work anymore
    System.out.println(b);
  }
  
  void testRandom(Random r, SplittableRandom sr, int x) {
    int val = r.nextInt(x);
    if (<warning descr="Condition 'val < 0' is always 'false'">val < 0</warning>) {}
    val = r.nextInt(100);
    if (<warning descr="Condition 'val >= 100' is always 'false'">val >= 100</warning>) {}
    if (val >= 99) {}
    val = sr.nextInt(x, 1000);
    if (<warning descr="Condition 'val >= 1000' is always 'false'">val >= 1000</warning>) {}
    val = sr.nextInt(10, 20);
    if (<warning descr="Condition 'val < 10' is always 'false'">val < 10</warning>) {}
    if (val <= 10) {}
    if (<warning descr="Condition 'val >= 20' is always 'false'">val >= 20</warning>) {}
    if (val >= 19) {}
  }
}
