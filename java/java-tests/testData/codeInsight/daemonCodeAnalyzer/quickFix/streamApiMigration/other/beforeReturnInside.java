// "Collapse loop with stream 'forEach()'" "false"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    for (String s : fo<caret>o) {
      if (s == null) {
        return s;
      }
    }
    return null;
  }
}
