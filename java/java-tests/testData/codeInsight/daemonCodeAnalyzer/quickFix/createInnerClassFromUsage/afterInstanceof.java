// "Create inner class 'Foo'" "true"
public class Test {
  boolean foo(Object o) {
    return o instanceof Foo;
  }

    private class Foo {
    }
}