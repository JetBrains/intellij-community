import java.util.List;
import java.util.Set;

class Main {
  record EmptyBox() {}
  record Point(int x, int y) {}
  record Rect(Point point1, Point point2) {}
  record Pair<T, U>(T t, U u) {}
  record Rec(Object obj) {}

  Point[] getPoints(int x) {
    return new Point[0];
  }

  void ok1(Point[] points) {
    for (Point(int x, int y) : points) {
      System.out.println(x + y);
    }
  }

  void ok2(EmptyBox[] emptyBoxes) {
    for (EmptyBox() : emptyBoxes) {
      System.out.println("Fill it up and send it back");
    }
  }

  void ok3(List<Point> points) {
    for (Point(final int a, final int b) : points) {
      System.out.println(a + b);
    }
  }

  void ok4(Iterable<Rect> rectangles) {
    for (Rect(Point(final int x1, final int y1), final Point point2) : rectangles) {
      System.out.println(x1 + y1);
    }
  }

  void test1() {
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>);
    for (Point(int x, int y) : getPoints(<error descr="Cannot resolve symbol 'x'">x</error>)) {
    }
    System.out.println(<error descr="Cannot resolve symbol 'y'">y</error>);
  }

  void test2(List<Integer> nums) {
    for (<error descr="Deconstruction pattern can only be applied to a record, 'java.lang.Integer' is not a record">Integer</error>(int num) : nums) {
      System.out.println();
    }
  }

  void test3(Point[] points) {
    for (Point(int x, <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer y</error>) : points) {
      System.out.println(x + y);
    }
  }

  void test4(Rect[] rectangles) {
    for (Rect(Point<error descr="Incorrect number of nested patterns: expected 2 but found 1">(int x1)</error>, Point point2): rectangles) {
      System.out.println(x1 + <error descr="Cannot resolve symbol 'y1'">y1</error>);
    }
  }

  void test5(Point[] points) {
    for (Point(int x, int y, <error descr="Incorrect number of nested patterns: expected 2 but found 3">int z)</error> : points) {
      System.out.println(x + y + z);
    }
  }

  <T> void test6(Set<Pair<String, String>> pairs) {
    for (<error descr="'Pair<String, String>' cannot be safely cast to 'Pair<T, String>'">Pair<T, String>(var t, var u)</error> : pairs) {}
  }

  void notExhaustive(Rec[] recs) {
    for (<error descr="Pattern 'Main.Rec' is not exhaustive on 'Main.Rec'">Rec(String s)</error>: recs) {
      System.out.println(s);
    }
  }

  void testNamedRecordPattern(Object obj, List<Rect> rectangles) {
    if (obj instanceof Point(int x, int y) <error descr="Identifier is not allowed here">point</error>) {
    }
    switch (obj) {
      case Point(int x, int y) <error descr="Identifier is not allowed here">point</error> -> System.out.println("point");
      case Rect(Point point1, Point(int x2, int y2) <error descr="Identifier is not allowed here">point2</error>) <error descr="Identifier is not allowed here">rect</error> -> System.out.println("rectangle");
    }

    for (Rect(Point(int x1, int y1) <error descr="Identifier is not allowed here">point1</error>, Point(int x2, int y2) <error descr="Identifier is not allowed here">point2</error>) : rectangles) {
      System.out.println("blah blah blah");
    }
  }

  void testInappropriateType(Object obj, String text) {
      for(Point(int x1, int y1) : <error descr="Foreach not applicable to type 'java.lang.Object'">obj</error>){
        System.out.println(x1);
      }
      for (Point(int x1, int y1) : <error descr="Foreach not applicable to type 'java.lang.String'">text</error>){
        System.out.println(x1);
      }
    }
}

