import static foo.Util.<error descr="Cannot resolve symbol 'baz'">baz</error>;

class Test {
  void test(Object <warning descr="Parameter 'baz' is never used">baz</warning>) {
    <error descr="Method call expected">baz()</error>;
  }
}