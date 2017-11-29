// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<Exception> foo = new ArrayList<>();
  {
    for (Exception e : fo<caret>o) {
      e.printStackTrace();
    }
  }
}
