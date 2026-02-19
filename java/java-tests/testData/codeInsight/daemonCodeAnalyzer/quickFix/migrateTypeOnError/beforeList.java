// "<html>Migrate 'list' type to 'String[]'</html>" "true"
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Demo {
  void test() {
    String s = 123;
    int x = new AtomicInteger();
    List<String> list = <caret>new String[]{"x", "y", "z"};
    System.out.println(s);
    System.out.println(x);
    System.out.println(list.get(0));
    System.out.println(list.size());
  }
}