import java.util.*;

class MyTest {
  void m(List<? extends F> l) {
    int b = ((<warning descr="Casting 'l.get(...)' to 'F' is redundant">F</warning>) l.get(0)).i;
  }

  static class F {
    int i;
  }

  List<?> getWildcard() {
    return Collections.emptyList();
  }

  List<String> getConcrete() {
    return Collections.emptyList();
  }

  void test() {
    ((<warning descr="Casting 'this' to 'MyTest' is redundant">MyTest</warning>)this).getConcrete();
    ((<warning descr="Casting 'this' to 'MyTest' is redundant">MyTest</warning>)this).getWildcard();
  }

}