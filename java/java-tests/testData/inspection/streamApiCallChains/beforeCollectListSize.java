// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.*;
import static java.util.stream.Collectors.toList;


public class Tests {
  void test(Collection<String> collection) {
    int s1 = collection.stream().filter(String::isEmpty).col<caret>lect(toList()).size();
    long s2 = collection.stream().filter(String::isEmpty).collect(toList()).size();
    if (collection.stream().filter(String::isEmpty).collect(toList()).size() == 0) {
      
    }
    if (collection.stream().filter(String::isEmpty).collect(toList()).size() > 0) {
      
    }
    if (collection.stream().filter(String::isEmpty).collect(toList()).size() + 1 > 0) {
      
    }
    if (collection.stream().filter(String::isEmpty).collect(toList()).isEmpty()) {
      
    }
    if (!collection.stream().filter(String::isEmpty).collect(toList()).isEmpty()) {
      
    }
  }
}
