// "Replace with addAll" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    Sample sm = new Sample();
    for (String s : fo<caret>o) {
      sm.foo.add(s);
    }
    return null;
  }
}
