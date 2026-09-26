// "Adapt 4th argument using 'Arrays.asList()'" "true-preview"
import java.util.List;

public class Test {

  void list(int i, int j, int k, List<String> l, String s) {

  }

  void m(String[] a) {
    list(1, 2, 3, a<caret>, "asd");
  }
}
