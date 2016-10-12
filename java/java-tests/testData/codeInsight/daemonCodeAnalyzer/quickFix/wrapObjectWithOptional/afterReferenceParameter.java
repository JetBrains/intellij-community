// "Wrap using 'java.util.Optional'" "true"
import java.util.Optional;

public class Test {

  void m(String ss) {
    f(Optional.ofNullable(ss));
  }


  void f(Optional<String> o) {

  }
}