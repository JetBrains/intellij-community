import static <error descr="Cannot resolve symbol 'foo'">foo</error>.bar.baz;

class Test {
  void test() {
    Object <warning descr="Variable 'baz' is never used">baz</warning> = null;
    <error descr="Method call expected">baz()</error>;
  }
}