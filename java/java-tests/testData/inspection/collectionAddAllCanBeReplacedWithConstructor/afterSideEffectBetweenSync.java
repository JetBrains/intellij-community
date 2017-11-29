// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Test {
  String foo;

  void test() {
    List<String> list;
    synchronized (this) {
      foo = "bar";
      list = new ArrayList<>(getSomething());
    }
    System.out.println(list);
  }

  private Collection<String> getSomething() {
    return Collections.singleton(foo);
  }

  public static void main(String[] args) {
    new Test().test();
  }
}
