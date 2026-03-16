<warning descr="Unused import statement">import static foo.Util.<error descr="Cannot resolve symbol 'baz'">baz</error>;</warning>

class Test {
  Object baz = null;

  void test() {
    <error descr="Method call expected">this.baz()</error>;
  }
}