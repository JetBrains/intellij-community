class Foo {
  private static final Foo A_PRIVATE;
  @Deprecated public static final Foo B_DEPRECATED;
  public static final Foo C_NORMAL;

}

class Bar {
  {
    Foo a = <caret>
  }
}