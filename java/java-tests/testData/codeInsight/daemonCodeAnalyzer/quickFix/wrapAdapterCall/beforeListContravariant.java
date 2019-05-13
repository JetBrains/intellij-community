// "Wrap using 'Collections.singletonList()'" "false"
import java.util.*;

class Test {

  void m(CharSequence s) {
    List<String> list = <caret>s;
  }

}