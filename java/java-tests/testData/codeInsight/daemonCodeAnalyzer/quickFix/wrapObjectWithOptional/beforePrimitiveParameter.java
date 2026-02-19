// "Wrap 2nd argument using 'java.util.Optional'" "true-preview"
import java.util.Optional;

public class Test {

  void m() {
    long ll = 10;
    f(10, l<caret>l, 10);
  }


  void f(long j, Optional<Number> o, int i) {

  }
}