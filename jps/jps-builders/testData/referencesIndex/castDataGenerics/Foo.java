import java.util.*;

class Foo {
  void m(Collection<String> c) {
    ((List<String>)c).get(1);
  }
}