// "Add on-demand static import for 'test.Foo'" "true-preview"
package test;

class Foo {
  public static void m() {}

  public static void main(String[] args) {
    Foo<caret>.m();
  }
}