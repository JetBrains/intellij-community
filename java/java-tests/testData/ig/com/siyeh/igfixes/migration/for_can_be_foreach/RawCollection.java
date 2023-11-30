import java.util.Iterator;
import java.util.List;

class RawCollection {

  void m2(List ss) {
    <caret>for (Iterator<String> iterator = ss.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      "".split(s);
    }
  }
}