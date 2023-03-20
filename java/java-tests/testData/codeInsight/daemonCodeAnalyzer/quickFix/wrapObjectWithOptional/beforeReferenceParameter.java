// "Wrap using 'java.util.Optional'" "true-preview"
import java.util.Optional;

public class Test {

  void m(String ss) {
    f(s<caret>s);
  }


  void f(Optional<String> o) {

  }
}