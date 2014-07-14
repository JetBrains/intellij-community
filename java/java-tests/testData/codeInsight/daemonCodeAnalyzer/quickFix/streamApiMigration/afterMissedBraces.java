// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
      foo.stream().filter(s -> s == null).forEach(s -> {
          int i = 0;
      });
    return null;
  }
}
