public class Foo {
    void m(Object o) {
      Integer string = (Integer.parseInt("test.test.cast    <caret>"));
    }
}