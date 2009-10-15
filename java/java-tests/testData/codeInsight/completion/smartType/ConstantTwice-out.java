class Key {}

public class Foo {
  static final Key FOO;

  void foo(Key key) {}

  {
    foo(FOO);<caret>
  }
}