// "Wrap using 'Collections.singletonList()'" "true"
import java.util.*;

class Test {

  void m(String s) {
    List<CharSequence> list = Collections.singletonList(s);
  }

}