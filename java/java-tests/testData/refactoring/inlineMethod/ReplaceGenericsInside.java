import java.util.*;
public class Super<T> {

  void <caret>foo() {
    List<T> l = new ArrayList<T>();
    for (T t : l) {
      System.out.println(t);
    }
  }
}

class Test extends Super<String>{
  void bar() {
    foo();
  }
}