// "Unwrap 'if' statement extracting side effects" "true-preview"
class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rect)) {
      return;
    }
    if (<caret>obj instanceof Rect(Point pos, Size size)) {
      System.out.println(pos);
      System.out.println(pos.x());
      System.out.println(size.h());
    }
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(Point pos, Size size) {
}
