// "Add on-demand static import for 'pkg.F'" "true-preview"
package pkg;

class F {
  private F. foo(int a, int b) {}

  <caret>F.Inner bar(int x, int y) {}

  static class Inner {}
}