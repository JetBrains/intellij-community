// "Replace with forEach" "true-preview"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
      foo.forEach(System.out::println);
  }
}
