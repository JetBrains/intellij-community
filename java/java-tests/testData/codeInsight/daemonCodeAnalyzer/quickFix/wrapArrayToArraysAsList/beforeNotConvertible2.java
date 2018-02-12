// "Wrap parameter using 'Arrays.asList()'" "false"
import java.util.LinkedList;

public class Test {

  void list(LinkedList<String> l) {

  }

  void m(Long[] a) {
    list(a<caret>);
  }
}
