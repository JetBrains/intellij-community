// "Wrap parameter using 'Arrays.asList()'" "true"
import java.util.List;

public class Test {

  void list(List<String> l) {

  }

  void m(String[] a) {
    list(a<caret>);
  }
}
