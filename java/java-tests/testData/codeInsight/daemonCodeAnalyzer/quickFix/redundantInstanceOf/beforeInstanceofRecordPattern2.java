// "Replace with a null check" "true-preview"

import org.jetbrains.annotations.Nullable;

class Test {
  void foo(@Nullable Rect obj) {
    if (<caret>obj instanceof Rect(Point point, Size size) rect) {
      System.out.println(42);
    }
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(Point pos, Size size) {
}
