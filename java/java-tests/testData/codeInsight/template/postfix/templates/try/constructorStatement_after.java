public class Foo {
  void m() {
      try {
          new Object()
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
}