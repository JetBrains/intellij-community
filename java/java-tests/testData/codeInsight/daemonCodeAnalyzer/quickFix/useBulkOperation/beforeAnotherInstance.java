// "Replace iteration with bulk 'Collection.addAll()' call" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  String foo(){
    Sample sm = new Sample();
    for (String s : foo) {
      <caret>sm.foo.add(s);
    }
    return null;
  }
}
