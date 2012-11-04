import java.util.Arrays;
import java.util.List;

class Foo {

  static void foo(List<String> ls) {
    List<String> ls2 = Arrays.asList(<caret>);
  }
}