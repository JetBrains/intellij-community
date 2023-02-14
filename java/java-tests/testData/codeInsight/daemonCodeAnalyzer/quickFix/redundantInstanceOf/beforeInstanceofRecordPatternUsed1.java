// "Replace with a null check" "false"

import org.jetbrains.annotations.Nullable;

class Test {
  void foo(@Nullable Rect rect) {
    if (<caret>rect instanceof Rect(Point point, Size size)) {
      System.out.println(size);
    }
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(Point pos, Size size) {
}
