// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Stream<String> names() {
    return Stream.of("foo", "bar");
  }

  public static void main(String[] args) {
      List<String> list = new ArrayList<>();
      for (Iterator<String> it = new Test().names().iterator(); it.hasNext(); ) {
          String name = it.next();
          String n = name.trim();
          if (!n.isEmpty()) {
              list.add(n);
          }
      }
  }
}