public class SomeClass {
  Border createBorder(Color color, int top, boolean isOpaque, int bottom, int right, int left) {
    new Insets(<caret>)
  }
}

class Insets {
  Insets(int top, int left, int bottom, int right) {}
}