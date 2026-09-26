// "Adapt argument using 'Arrays.asList()'" "true-preview"
import java.util.Arrays;
import java.util.List;

public class Test {

  void list(List<String> l) {

  }

  void m(String[] a) {
    list(Arrays.asList(a));
  }
}
