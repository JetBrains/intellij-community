interface Bar {}
class Goo {}

abstract class Foo {
    void foo(Bar f);

    void foo(Foo f);

    void foo(Goo f);


  {

    foo((<caret>))
  }
}