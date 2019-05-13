// "Wrap using 'java.util.Optional'" "true"
import java.util.Optional;

public class Test {

  void m() {
    Optional<String> o = "some <caret>string value";
  }

}