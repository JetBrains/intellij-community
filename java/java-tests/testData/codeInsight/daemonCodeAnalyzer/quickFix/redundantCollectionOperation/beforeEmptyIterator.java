// "Replace with 'Collections.emptyIterator()'" "true-preview"
import java.util.*;

class Test {
  Iterator<String> test() {
    return new ArrayList<String>().iter<caret>ator();
  }
}