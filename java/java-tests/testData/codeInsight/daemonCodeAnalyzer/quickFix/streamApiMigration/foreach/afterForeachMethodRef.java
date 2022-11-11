// "Replace with forEach" "true-preview"
import java.util.ArrayList;
import java.util.List;

class Sample {
  List<Exception> foo = new ArrayList<>();
  {
      foo.forEach(Throwable::printStackTrace);
  }
}
