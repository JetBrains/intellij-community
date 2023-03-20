class A {
  public void foo() {
    @X final int a = 1, d = 2<caret>, /*+*/ b = 2, c = 3;
  }
}
@interface X {}