// "Replace with forEach" "false"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
    for (String s : fo<caret>o) {
      if (s == null) {
        continue;
      }
    }

  }
}
