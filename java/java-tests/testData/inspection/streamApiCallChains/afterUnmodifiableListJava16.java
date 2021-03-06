// "Replace 'collect(toUnmodifiableList())' with 'toList()'" "true"
import java.util.List;
import java.util.stream.*;

class X {
  void test(Stream<String> stream) {
    List<String> list = stream.toList();
  }
}