class QTest {
  {
    System.out.println(Foo.BA<caret>R);
  }
}

class Foo {
  public static final String FOO = "FOO";
  public static final String BAR = FOO;
}