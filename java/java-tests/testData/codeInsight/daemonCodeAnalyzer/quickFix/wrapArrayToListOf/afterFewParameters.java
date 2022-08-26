// "Adapt 4th argument using 'List.of()'" "true-preview"
import java.util.List;

public class Test {

  void list(int i, int j, int k, List<String> l, String s) {

  }

  void m(String[] a) {
    list(1, 2, 3, List.of(a), "asd");
  }
}
