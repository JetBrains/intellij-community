import java.util.Arrays;

class Foo {
  void foo(String[] array) {
    Arrays.stream(array).map(<caret>)
  }
}