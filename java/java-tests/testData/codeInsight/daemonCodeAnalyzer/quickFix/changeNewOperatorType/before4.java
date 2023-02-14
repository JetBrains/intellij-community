// "Change 'new ArrayList<Integer>()' to 'new ArrayList<String>()'" "true-preview"
import java.util.*;

class RRR {
  void f() {
     List<String> l = new <caret>ArrayList<Integer>();
  }
}