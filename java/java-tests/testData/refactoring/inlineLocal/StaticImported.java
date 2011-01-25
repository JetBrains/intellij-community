import java.util.ArrayList;
import java.util.List;

import static Statics._emptyList;

public class Statics {

  public static <T> List<T> _emptyList() {
    return new ArrayList<T>();
  }
}


class Foo {
  public static void main(String[] args) {
    List<String> v1 = _emptyList();
    doSomething(v<caret>1);
  }

  public static void doSomething(List<String> list) {

  }
}