// "<html>Migrate 'x' type to 'AtomicInteger'</html>" "true"
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Demo {
  void test() {
    String s = 123;
    AtomicInteger x = new AtomicInteger();
    List<String> list = new String[]{"x", "y", "z"};
    System.out.println(s);
    System.out.println(x.get());
    System.out.println(list.get(0));
    System.out.println(list.size());
  }
}