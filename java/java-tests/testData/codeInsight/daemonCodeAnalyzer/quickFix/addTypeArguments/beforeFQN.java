// "Add explicit type arguments" "true-preview"
import java.util.List;
import java.util.Collections;

class Bar {
  public static void main(String[] args) {
    new Foo().fo<caret>o(Collections.emptyList());
  }
}

class Foo {
  void foo(List<java.util.Date> dates) { }
}