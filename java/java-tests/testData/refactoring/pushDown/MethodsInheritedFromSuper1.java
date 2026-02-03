@interface Anno {}
interface A {
  @Anno
  default String <caret>m() {}
}

class FooBar {
  public String m() {}
}

class B extends FooBar implements A {
}