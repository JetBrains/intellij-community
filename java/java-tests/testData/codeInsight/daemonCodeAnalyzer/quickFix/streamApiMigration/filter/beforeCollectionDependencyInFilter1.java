// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

abstract class Sample implements List<String> {
  void main() {
    for (final String tag : t<caret>his) {
      if (!contains(tag.trim())) {
        add(tag.trim());
      }
    }
  }
}
