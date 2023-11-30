// "Fuse PriorityQueue into the Stream API chain" "false"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  void test(Stream<String> s) {
    PriorityQueue<String> strings = new PriorityQueue<>(s.<caret>collect(Collectors.toList()));
  }
}