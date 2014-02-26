// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
    for (String s : fo<caret>o) {
      bar(s)
    }
  }
  
  void bar(String s){}
}
