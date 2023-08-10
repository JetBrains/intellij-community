// "<html>Migrate 'list' type to 'String[]'</html>" "true"
import java.util.concurrent.atomic.AtomicInteger;

class Demo {
  void test() {
    String s = 123;
    int x = new AtomicInteger();
    String[] list = new String[]{"x", "y", "z"};
    System.out.println(s);
    System.out.println(x);
    System.out.println(list[0]);
    System.out.println(list.length);
  }
}