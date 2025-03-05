import <error descr="Cannot resolve symbol 'foo'">foo</error>.bar.*<caret>;

class Foo {
  void test(<error descr="Cannot resolve symbol 'Baz'">Baz</error> <warning descr="Parameter 'baz' is never used">baz</warning>) {}
}