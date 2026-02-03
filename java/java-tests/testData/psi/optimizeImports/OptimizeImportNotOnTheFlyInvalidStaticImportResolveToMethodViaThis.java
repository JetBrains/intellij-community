<warning descr="Unused import statement">import static foo.Util.<error descr="Cannot resolve symbol 'baz'">baz</error>;</warning>

class Test {
  void baz() {}

  void test() {
    this.baz();
  }
}