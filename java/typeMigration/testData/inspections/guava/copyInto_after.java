import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CopyInto {
  void m(Stream<String> it) {
    ArrayList<String> collection = new ArrayList<>();
    List<String> collection2 = it.collect(Collectors.toCollection(() -> collection));
  }
}