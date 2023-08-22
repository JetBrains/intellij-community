// "Unwrap 'if' statement extracting side effects" "true-preview"
import org.jetbrains.annotations.NotNull;

class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rect)) {
      return;
    }
      Rect rect = (Rect) obj;
      Point pos = rect.pos();
      double x = pos.x();
      double y = pos.y();
      Size size = rect.size();
      double w = size.w();
      double h = size.h();
      System.out.println(rect);
      System.out.println(rect.size());
      System.out.println(pos);
      System.out.println(pos.x());
      System.out.println(h);
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(@NotNull Point pos, @NotNull Size size) {
}
