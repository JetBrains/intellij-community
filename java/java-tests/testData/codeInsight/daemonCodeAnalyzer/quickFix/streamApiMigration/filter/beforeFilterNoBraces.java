// "Collapse loop with stream 'forEach()'" "true-preview"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
    for (String s : fo<caret>o) {
      if (s != null) System.out.println(s);
    }
  }
}
