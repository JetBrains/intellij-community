// "Fix all 'Comparison of compare method result with specific constant' problems in file" "true"
import java.util.*;

class Test {
  String test(Comparable<?> c1, Comparable<?> c2) {
    int result = c1.compareTo(c2);
    if(0 == result) {
      return "equal";
    } else if(0 > result) {
      return "less";
    } else if(0 < result) {
      return "greater";
    } else {
      return "impossible";
    }
  }
}