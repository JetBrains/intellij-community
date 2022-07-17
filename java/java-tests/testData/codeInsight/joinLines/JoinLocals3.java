class A {
  public void foo() {
    @X final int a = 1, d = 2;<caret>
    @X final int /*+*/ b = 2, c = 3;
  }
}
@interface X {}