// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

abstract class Sample implements List<String> {
  void main() {
    for (final String tag : t<caret>his) {
      if (!foo(this)) {
        add(tag.trim());
      }
    }
  }
  
  static boolean foo(List<String> a){ return false;}
}
