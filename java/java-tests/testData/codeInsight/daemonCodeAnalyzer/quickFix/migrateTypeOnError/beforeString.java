// "<html>Migrate 's' type to 'int'</html>" "false"
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Demo {
  void test() {
    String s = <caret>123;
    int x = new AtomicInteger();
    List<String> list = new String[]{"x", "y", "z"};
    System.out.println(s);
    System.out.println(x);
    System.out.println(list.get(0));
    System.out.println(list.size());
  }
}