// "Wrap using 'Collections.singletonList()'" "false"
import java.util.*;

class Test {

  void m(long l) {
    List<Integer> list = <caret>l;
  }

}