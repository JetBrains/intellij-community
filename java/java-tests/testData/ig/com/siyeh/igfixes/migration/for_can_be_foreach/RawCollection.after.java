import java.util.Iterator;
import java.util.List;

class RawCollection {

  void m2(List ss) {
      for (String s : (Iterable<String>) ss) {
          "".split(s);
      }
  }
}