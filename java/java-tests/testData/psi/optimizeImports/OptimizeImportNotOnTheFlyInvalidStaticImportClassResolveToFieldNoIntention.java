import static foo.Util.<error descr="Cannot resolve symbol 'baz'">baz</error>;

class Test {
  Object baz = null;

  void test() {
    <error descr="Method call expected">baz()</error>;
  }
}