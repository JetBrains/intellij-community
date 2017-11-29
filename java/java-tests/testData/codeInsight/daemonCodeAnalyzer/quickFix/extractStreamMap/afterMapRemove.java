// "Extract variable 'y' to 'map' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testRemoveMap() {
      Stream.of("xyz").map(x -> x + x).map(y -> y).forEach(System.out::println);
  }
}