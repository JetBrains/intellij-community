class Bar {}

class Foo {
  Foo myFoo;

  Bar getBar() {
    return myFoo.getBar();<caret>
  }
}