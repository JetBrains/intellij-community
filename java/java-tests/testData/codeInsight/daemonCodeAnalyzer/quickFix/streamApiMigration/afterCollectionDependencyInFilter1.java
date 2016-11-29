// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

abstract class Sample implements List<String> {
  void main() {
      this.stream().filter(tag -> !contains(tag.trim())).forEach(tag -> add(tag.trim()));
  }
}
