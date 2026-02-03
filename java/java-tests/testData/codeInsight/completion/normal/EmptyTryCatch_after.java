class X{
  public void test() {
    try {
    } catch (Exception e) {
        <selection>throw new RuntimeException(e);</selection><caret>
    }
  }
}
