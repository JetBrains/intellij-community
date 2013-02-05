interface Foo {
  public static void bar1() {}
  public <error descr="Illegal combination of modifiers: 'abstract' and 'static'">abstract</error> static void bar2() {}
}