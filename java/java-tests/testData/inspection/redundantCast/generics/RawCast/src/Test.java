import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();

        Map<String, String> map = (Map) properties;
        System.out.println(map);
    }
}

interface I {}
class C implements I {}
class U {
  void foo() {
    List<C> listOfC = null;
    List<I> listOfI = (List) listOfC;
  }
}
