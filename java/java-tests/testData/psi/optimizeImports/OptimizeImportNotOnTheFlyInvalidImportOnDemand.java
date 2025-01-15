import <error descr="Cannot resolve symbol 'foo'">foo</error>.bar.*;
<warning descr="Unused import statement">import static java.util.Arrays.asList<caret>;</warning>

class Foo {
  void test(<error descr="Cannot resolve symbol 'Baz'">Baz</error> <warning descr="Parameter 'baz' is never used">baz</warning>) {}
}