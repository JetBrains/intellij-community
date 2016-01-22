// "Replace with addAll" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    Sample sm = new Sample();
      sm.foo.addAll(foo);
    return null;
  }
}
