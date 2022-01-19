public class Foo {
  void m() {
      try {
          Object obj = new Object()<caret>
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }
}