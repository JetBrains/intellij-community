@interface Anno {}
interface A {
}

class FooBar {
  public String m() {}
}

class B extends FooBar implements A {
    @Anno
    public String m() {}
}