import java.util.*;

class IteratorUnboxing {

  void m(List<Integer> children) {
    for<caret> (Iterator<Integer> iterator = children.iterator(); iterator.hasNext();) {
      int child = iterator.next();
      if (child == Integer.valueOf(10000)) {
        throw null;
      }
    }
  }
}