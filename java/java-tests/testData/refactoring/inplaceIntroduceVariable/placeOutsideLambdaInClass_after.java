class MyTest {
  interface I {
    String f(String s);
  }
  I i;

    {
        String expr = "extract me";
        i = s -> expr;
    }
}