import java.util.*;

class A<T> {
  protected T t;
  protected List<T> list = new ArrayList<T>();
}

public class B extends A<S<caret>tring> {
  void foo() {
    if (t == null) return;
    if (list == null) return;
    System.out.println(t);
    for (String s : list) {
      //do nothing
    }
  }
}