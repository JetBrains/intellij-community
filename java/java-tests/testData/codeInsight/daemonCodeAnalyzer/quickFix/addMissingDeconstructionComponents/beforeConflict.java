// "Add missing nested patterns" "true-preview"
class Main {
    void foo(Object obj) {
      double x = 0.0;
      double y = 0.0;
        switch (obj) {
            case Point(<caret>) -> {}
            default -> {}
        }
    }

    record Point(double x, double y) {}
}
