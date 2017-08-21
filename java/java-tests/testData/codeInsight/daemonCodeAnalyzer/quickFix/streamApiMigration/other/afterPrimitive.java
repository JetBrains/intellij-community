// "Replace with forEach" "true"
import java.util.List;

class Sample {
  public void some(List<String> from, List<Integer> to) {
      from.stream().map(String::length).forEach(to::add);
  }
}
