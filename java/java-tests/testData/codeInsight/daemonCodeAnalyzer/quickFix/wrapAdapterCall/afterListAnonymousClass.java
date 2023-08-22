// "Adapt using 'Collections.singletonList()'" "true-preview"
import java.util.*;

class Test {

  void m() {
    List<Object> list = Collections.singletonList(new Object() {
    });
  }

}