// "Extract variable 'y' to separate stream step" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testRemoveMap() {
    Stream.of("xyz").map(x -> x + x).forEach(System.out::println);
  }
}