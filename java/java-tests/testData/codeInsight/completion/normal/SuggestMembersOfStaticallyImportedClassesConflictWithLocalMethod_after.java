import foo.Foo;

import static foo.Foo.foo;

class Bar {
  {
    foo();
    Foo.bar();<caret>
  }

  void bar();
}
