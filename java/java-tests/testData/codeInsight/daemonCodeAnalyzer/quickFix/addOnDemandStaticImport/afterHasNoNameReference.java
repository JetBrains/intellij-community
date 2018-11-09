// "Add on demand static import for 'pkg.F'" "true"
package pkg;

class F {
  private F. foo(int a, int b) {}

  Inner bar(int x, int y) {}

  static class Inner {}
}