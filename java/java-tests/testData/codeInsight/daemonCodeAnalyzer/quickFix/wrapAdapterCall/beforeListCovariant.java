// "Adapt using 'Collections.singletonList()'" "true-preview"
import java.util.*;

class Test {

  void m(String s) {
    List<CharSequence> list = <caret>s;
  }

}