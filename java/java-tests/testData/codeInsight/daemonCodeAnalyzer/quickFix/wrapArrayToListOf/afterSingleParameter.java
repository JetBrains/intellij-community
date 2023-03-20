// "Adapt argument using 'List.of()'" "true-preview"
import java.util.List;

public class Test {

  void list(List<String> l) {

  }

  void m(String[] a) {
    list(List.of(a));
  }
}
