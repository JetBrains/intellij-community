public class Foo {

  void foo() {
    Zoo z = xcreate<caret>x
  }

}

class Zoo { }

class ZooFactory {
  static Zoo xcreateZoo() {}
  static Zoo xcreateZoo(int a) {}
  static Object xcreateElephant() {}
}