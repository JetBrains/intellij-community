// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
      foo.stream().filter(Objects::isNull).forEach(s -> {
          int i = 0;
      });
    return null;
  }
}
