class Test {
  interface I {
    <error descr="Extension methods are not supported at language level '1.7'">static void foo() {}</error>
  }
  
  abstract static class IImpl implements I {}
  interface I2 extends I {}

  {
    I.<error descr="Static interface method calls are not supported at language level '1.7'">foo</error>();
    IImpl.<error descr="Static interface method calls are not supported at language level '1.7'">foo</error>();
    I2.<error descr="Static interface method calls are not supported at language level '1.7'">foo</error>();
  }
}