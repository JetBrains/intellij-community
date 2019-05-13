import java.util.Map;
public class Test {
    Map<Integer, Integer> f;
    void foo() {
      for (Integer i  : f.keySet()) {}
    }
}
