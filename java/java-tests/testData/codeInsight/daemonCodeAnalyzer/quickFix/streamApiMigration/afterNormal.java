// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<String> foo = new ArrayList<>();
  {
      foo.stream().filter(s -> s != null).forEach(System.out::println);
  }
}
