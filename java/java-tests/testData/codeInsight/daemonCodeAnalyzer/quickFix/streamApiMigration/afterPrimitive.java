// "Replace with collect" "true"
import java.util.List;
import java.util.stream.Collectors;

class Sample {
  public void some(List<String> from, List<Integer> to) {
      to.addAll(from.stream().map(String::length).collect(Collectors.toList()));
  }
}
