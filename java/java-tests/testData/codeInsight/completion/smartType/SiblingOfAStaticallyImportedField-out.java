import static Bar.BAR;
import static Bar.FOO;

class Bar {
  static String FOO;
  static String BAR;
}

class Foo {

  Object[] foo() {
    String a = FOO;
    String b = BAR;<caret>
  }
}