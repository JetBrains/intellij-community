// "Replace with List.sort" "true"
import java.util.Collections;
import java.util.List;

public class Main {
  void sortChildren() {
    Collections.so<caret>rt(getChildren(), Comparator.naturalOrder());
  }

  List<String> getChildren() {
    return new ArrayList<>();
  }
}