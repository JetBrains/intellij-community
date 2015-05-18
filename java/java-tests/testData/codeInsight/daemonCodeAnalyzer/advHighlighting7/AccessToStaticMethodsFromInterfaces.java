class Test {
  interface I {
    <error descr="Extension methods are not supported at this language level">static void foo() {}</error>
  }
  
  abstract class IImpl implements I {}
  interface I2 extends I {}

  {
    <error descr="Static interface method invocations are not supported at this language level">I.foo();</error>
    IImpl.<error descr="Static interface method invocations are not supported at this language level">foo</error>();
    I2.<error descr="Static interface method invocations are not supported at this language level">foo</error>();
  }
}