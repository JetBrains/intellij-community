import java.util.*;
class Test {
 <T> void foo(T t, final ArrayList<T> anObject) {
    List<T> ls = anObject;
  }

  void bar() {
    String s = "";
    foo(s, new ArrayList<String>());
  }
}
