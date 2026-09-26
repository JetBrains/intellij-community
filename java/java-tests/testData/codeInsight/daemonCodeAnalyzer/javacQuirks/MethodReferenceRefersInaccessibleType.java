package bar;

import foo.Foo;

import java.util.function.Supplier;

class Baz {
  public static  void foo(Supplier<Object> supplier) {
    System.out.println(supplier.get());
  }

  public static void main(String[] args) {
    Baz.foo(<warning descr="Target method return type mentions inaccessible class foo.Foo.Private, this will cause an IllegalAccessError at runtime">Foo::m0</warning>);
    Baz.foo(<warning descr="Target method return type mentions inaccessible class foo.Foo.Private, this will cause an IllegalAccessError at runtime">Foo::m1</warning>);
    Baz.foo(<warning descr="Target method return type mentions inaccessible class foo.Foo.Private, this will cause an IllegalAccessError at runtime">Foo::m2</warning>);
    Baz.foo(<warning descr="Target method return type mentions inaccessible class foo.Foo.Package, this will cause an IllegalAccessError at runtime">Foo::m3</warning>);
    Baz.foo(Foo::m4);
    Baz.foo(Foo::m5);
  }
}