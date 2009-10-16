class Smth {}
class Button extends Smth {}
class ButtonBase extends Button {}

class Foo {
  Foo foo(String s) {
    Smth s = new B<caret>
  }
}