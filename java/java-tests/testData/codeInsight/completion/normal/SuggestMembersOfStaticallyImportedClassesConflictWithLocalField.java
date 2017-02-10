import static foo.Foo.foo;

class Bar {
  {
    System.out.println(foo);
    System.out.println(ba<caret>x);
  }

  int bar = 42;
}
