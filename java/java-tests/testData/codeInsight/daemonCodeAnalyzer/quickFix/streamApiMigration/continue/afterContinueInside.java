// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Sample {
  List<String> foo = new ArrayList<>();
  {
      foo.stream().filter(Objects::isNull).forEach(s -> {
          return;
      });

  }
}