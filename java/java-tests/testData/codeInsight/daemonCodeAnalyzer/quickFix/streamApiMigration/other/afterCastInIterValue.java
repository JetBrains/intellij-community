// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
      ((List<String>) foo).stream().filter(Objects::isNull).forEach(System.out::println);
    return null;
  }
}
