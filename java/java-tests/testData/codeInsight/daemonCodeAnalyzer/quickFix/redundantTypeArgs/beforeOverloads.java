// "Remove type arguments" "false"
class Test {

  public void valueOfPasses() {
    assertEquals("a string", String.valueOf(this.<Obj<caret>ect>someProperty()));
  }

  public static void assertEquals(Object expected, Object actual) {}

  public <T> T someProperty() {
    //noinspection unchecked
    return (T) "a string";
  }

}
