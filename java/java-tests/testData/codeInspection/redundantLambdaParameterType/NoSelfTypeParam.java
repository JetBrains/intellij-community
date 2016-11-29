// "Remove redundant types" "true"
import java.util.*;

public class Sample {
  List<String> foo = new ArrayList<>();
  {
    foo.forEach((Str<caret>ing s) -> {});
  }
}

