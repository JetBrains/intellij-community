// "Create inner class 'Foo'" "true-preview"
public class Test {
  boolean foo(Object o) {
    return o instanceof Foo;
  }

    private class Foo {
    }
}