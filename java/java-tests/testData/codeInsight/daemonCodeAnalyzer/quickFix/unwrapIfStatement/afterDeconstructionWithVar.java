// "Unwrap 'if' statement extracting side effects" "true-preview"
class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rect)) {
      return;
    }
      Rect rect = (Rect) obj;
      Point pos = rect.pos();
      Size size = rect.size();
      System.out.println(rect);
      System.out.println(rect.size());
      System.out.println(pos);
      System.out.println(pos.x());
      System.out.println(size.h());
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(Point pos, Size size) {
}
