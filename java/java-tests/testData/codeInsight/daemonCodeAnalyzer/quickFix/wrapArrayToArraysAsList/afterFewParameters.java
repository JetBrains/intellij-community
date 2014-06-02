// "Wrap 4th parameter using 'Arrays.asList'" "true"
import java.util.Arrays;
import java.util.List;

public class Test {

  void list(int i, int j, int k, List<String> l, String s) {

  }

  void m(String[] a) {
    list(1, 2, 3, Arrays.asList(a), "asd");
  }
}
