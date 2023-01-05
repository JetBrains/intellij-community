import java.util.List;
import java.util.Set;

class Main {
  record Point(int x, int y) {}
  record Rect(Point point1, Point point2) {}
  record Pair<T, U>(T t, U u) {}
  record Rec(Object obj) {}

  Point[] getPoints(int x) {
    return new Point[0];
  }

  void test1(Point[] points) {
    for (Point(int x, int y) : points) {
      System.out.println(x + y);
    }
  }

  void test2() {
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>);
    for (Point(int x, int y) : getPoints(<error descr="Cannot resolve symbol 'x'">x</error>)) {
    }
    System.out.println(<error descr="Cannot resolve symbol 'y'">y</error>);
  }

  void test3(List<Integer> nums) {
    for (<error descr="Deconstruction pattern can only be applied to a record, 'java.lang.Integer' is not a record">Integer</error>(int num) : nums) {
      System.out.println();
    }
  }

  void test4(Point[] points) {
    for (Point(int x, <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer y</error>) : points) {
      System.out.println(x + y);
    }
  }

  void test5(Rect[] rectangles) {
    for (Rect(Point<error descr="Incorrect number of nested patterns: expected 2 but found 1">(int x1)</error>, Point point2): rectangles) {
      System.out.println(x1 + <error descr="Cannot resolve symbol 'y1'">y1</error>);
    }
  }

  void test6(Point[] points) {
    for (Point(int x, int y, <error descr="Incorrect number of nested patterns: expected 2 but found 3">int z)</error> : points) {
      System.out.println(x + y + z);
    }
  }

  <T> void test7(Set<Pair<String, String>> pairs) {
    for (<error descr="'Main.Pair<java.lang.String,java.lang.String>' cannot be safely cast to 'Main.Pair<T,java.lang.String>'">Pair<T, String>(var t, var u)</error> : pairs) {}
  }

  void notExhaustive(Rec[] recs) {
    for (<error descr="Pattern 'Main.Rec' is not exhaustive on 'Main.Rec'">Rec(String s)</error>: recs) {
      System.out.println(s);
    }
  }
}
