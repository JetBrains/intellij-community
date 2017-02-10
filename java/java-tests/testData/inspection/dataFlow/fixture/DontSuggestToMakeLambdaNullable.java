import java.util.Random;
import java.util.List;

class TestCompilerWarnings {
  long foo(List<String> l) {
    return l.stream().map(s -> {
      if (new Random().nextInt() > 2) {
        return null;
      }
      else {
        return s;
      }
    }).count();
  }

}