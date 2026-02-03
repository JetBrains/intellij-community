// "Wrap using 'java.util.Optional'" "false"
import java.util.Optional;

public class Test {

  void m(String ss) {
    f(<caret>ss);
  }


  void f(Optional<Long> o) {

  }
}