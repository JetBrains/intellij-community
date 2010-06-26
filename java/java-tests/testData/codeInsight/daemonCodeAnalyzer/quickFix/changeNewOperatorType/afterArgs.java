// "Change 'new ArrayList<Integer>(2)' to 'new ArrayList<String>()'" "true"
import java.util.*;

class RRR {
  void f() {
     List<String> l = new ArrayList<String>(2<caret>);
  }
}