// "Replace with forEach" "true-preview"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
      //some comment
      foo.forEach(System.out::println);
  }
}
