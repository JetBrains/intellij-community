public class Foo {
  void m() {
      try {
          Object obj = new Object()<caret>
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
}