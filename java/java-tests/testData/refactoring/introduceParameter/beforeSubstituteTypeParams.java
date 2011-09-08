import java.util.*;
class Test {
 <T> void foo(T t) {
    List<T> ls = <selection>new ArrayList<T>()</selection>;
  }

  void bar() {
    String s = "";
    foo(s);
  }
}
