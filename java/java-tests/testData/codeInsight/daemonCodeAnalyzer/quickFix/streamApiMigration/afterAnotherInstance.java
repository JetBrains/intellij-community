// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    Sample sm = new Sample();
      sm.foo.addAll(foo.stream().collect(Collectors.toList()));
    return null;
  }
}
