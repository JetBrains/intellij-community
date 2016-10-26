// "Replace with collect" "true"
import java.util.*;

class A {
  public List<? super Integer> sum() {
    List<? super Integer> result = new ArrayList<>();
    for(i<caret>nt i=0; i<10; i++) {
      result.add(i*2);
    }
    return result;
  }
}