// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.*;
import static java.util.stream.Collectors.toList;


public class Tests {
  void test(Collection<String> collection) {
    int s1 = (int) collection.stream().filter(String::isEmpty).count();
    long s2 = collection.stream().filter(String::isEmpty).count();
    if (collection.stream().filter(String::isEmpty).count() == 0) {
      
    }
    if (collection.stream().filter(String::isEmpty).count() > 0) {
      
    }
    if ((int) collection.stream().filter(String::isEmpty).count() + 1 > 0) {
      
    }
    if (collection.stream().filter(String::isEmpty).count() == 0) {
      
    }
    if (!(collection.stream().filter(String::isEmpty).count() == 0)) {
      
    }
  }
}
