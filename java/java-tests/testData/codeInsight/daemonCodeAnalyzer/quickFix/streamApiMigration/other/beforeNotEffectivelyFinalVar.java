// "Replace with forEach" "false"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    boolean b = true;
    for (String s : f<caret>oo) {
      if (s == null) {
        b = false;
      }
    }
    return null;
  }
}
