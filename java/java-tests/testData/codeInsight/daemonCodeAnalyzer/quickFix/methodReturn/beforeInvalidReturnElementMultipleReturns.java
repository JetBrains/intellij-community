// "Make 'm' return 'java.util.AbstractList<java.lang.Object>' or predecessor" "true"
import java.util.*;

class Test {

  void <caret>m(boolean b) {
    if (b) return new ArrayList<>();
    return new LinkedList<>();
  }

}