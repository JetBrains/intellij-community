// "Convert to 'new Color(100, 120, 140, 37)'" "true"

package java.awt;

class A {
  private Color color = new Color(100, 120, 140, 37);
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
