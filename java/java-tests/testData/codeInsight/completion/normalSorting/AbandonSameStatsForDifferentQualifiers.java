class Foo1 { boolean method1() {} }
class Foo2 { boolean method2() {} }
class Foo3 { boolean method3() {} }

public class MyFirstTestClassFoo {

  void foo(Foo1 f1, Foo2 f2, Foo3 f3) {
    f1.<caret>
  }

}
