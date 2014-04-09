// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample extends ArrayList<String> {
  void main() {
    for (final String tag : t<caret>his) {
      if (!super.contains(tag)) {
        add(tag.trim());
      }
    }
  }
  
  static boolean foo(List<String> a){ return false;}
}
