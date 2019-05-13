public class Foo {
    void m(String s) {
      Object o = s.isEmpty() ? s.concat("a").cast<caret> : s.concat("b");
    }
}