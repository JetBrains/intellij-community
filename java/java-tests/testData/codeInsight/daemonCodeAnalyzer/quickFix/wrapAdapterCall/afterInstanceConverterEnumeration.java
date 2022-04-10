// "Adapt using 'asIterator()'" "true"
import java.util.*;
import java.util.stream.*;

class Test {
  void testIterator(Enumeration<String> en) {
    Iterator<String> iterator = en.asIterator();
  }
}