// "Add explicit type arguments" "true"
import java.util.Date;
import java.util.List;
import java.util.Collections;

class Bar {
  public static void main(String[] args) {
    new Foo().foo(Collections.<Date>emptyList());
  }
}

class Foo {
  void foo(List<java.util.Date> dates) { }
}