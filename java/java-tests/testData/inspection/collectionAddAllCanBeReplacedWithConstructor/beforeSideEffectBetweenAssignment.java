// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.*;

public class Test {
  String foo;

  void test() {
    final List<String> list;
    if(Math.random() > 0) {
      list = new ArrayList<>();
      foo = "bar";
      list.a<caret>ddAll(getSomething());
      System.out.println(list);
    } else {
      list = new LinkedList<>();
    }
  }

  private Collection<String> getSomething() {
    return Collections.singleton(foo);
  }

  public static void main(String[] args) {
    new Test().test();
  }
}
