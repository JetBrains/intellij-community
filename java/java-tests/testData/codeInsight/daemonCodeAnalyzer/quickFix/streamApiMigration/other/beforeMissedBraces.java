// "Collapse loop with stream 'forEach()'" "true-preview"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    for (String s : fo<caret>o) {
      if (s == null) bar();
    }
    return null;
  }

  bar() {}
}
