// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    for (String s : (List<String>)fo<caret>o) {
      if (s == null) {
        System.out.println(s);
      }
    }
    return null;
  }
}
