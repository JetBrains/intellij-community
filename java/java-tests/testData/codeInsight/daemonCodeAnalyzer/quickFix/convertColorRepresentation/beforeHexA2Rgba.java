// "Convert to 'new Color(37, 100, 120, 140)'" "true"

package java.awt;

class A {
  private Color color = new Color(0x25647<caret>88c, true);
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
