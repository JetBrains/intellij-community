// "Fuse ArrayList into the Stream API chain" "true-preview"
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  void test(Stream<String> stream) {
    List<Object> objects = stream.distinct().collect(Collectors.toList());
  }
}
