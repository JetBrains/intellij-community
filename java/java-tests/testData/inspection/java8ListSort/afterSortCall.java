// "Replace with List.sort" "true"
import java.util.Collections;
import java.util.List;

public class Main {
  void sortChildren() {
    getChildren().sort(Comparator.naturalOrder());
  }

  List<String> getChildren() {
    return new ArrayList<>();
  }
}