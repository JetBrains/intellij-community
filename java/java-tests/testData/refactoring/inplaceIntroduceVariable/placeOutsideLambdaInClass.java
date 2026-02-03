class MyTest {
  interface I {
    String f(String s);
  }
  I i = s -> "extract<caret> me";
}