// "Convert to 'new Color(0x10101)'" "true"

package java.awt;

class A {
  private Color color = new Color(257, 257,<caret> 257);
}

class Color {
  Color(int r, int g, int b) {
  }

  Color(int r, int g, int b, int a) {
  }

  Color(int rgb) {
  }

  Color(int rgba, boolean hasAlpha) {
  }
}
