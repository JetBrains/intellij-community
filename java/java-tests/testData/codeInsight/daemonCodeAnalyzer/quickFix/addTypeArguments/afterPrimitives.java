// "Add explicit type arguments to 2nd argument" "true-preview"
import java.util.Collections;
import java.util.List;

class Foo {

  public static void main(String[] args) {
    new Foo(1, Collections.<String>emptyList());
  }

  public Foo(Integer i, List<String> list) {

  }
}