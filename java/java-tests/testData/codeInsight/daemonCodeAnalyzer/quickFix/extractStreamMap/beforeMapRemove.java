// "Extract variable 'y' to 'map' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testRemoveMap() {
    Stream.of("xyz").map(x -> {
      String <caret>y = x+x;
      return y;
    }).forEach(System.out::println);
  }
}