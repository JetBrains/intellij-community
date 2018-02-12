// "Wrap parameter using 'Arrays.asList()'" "false"
import java.util.LinkedList;

public class Test {

  void list(LinkedList<String> l) {

  }

  void m(String[] a) {
    list(a<caret>);
  }
}
