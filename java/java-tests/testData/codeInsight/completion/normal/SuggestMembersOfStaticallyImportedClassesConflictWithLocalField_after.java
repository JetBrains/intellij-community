import foo.Foo;

import static foo.Foo.foo;

class Bar {
  {
    System.out.println(foo);
    System.out.println(Foo.bar<caret>);
  }

  int bar = 42;
}
