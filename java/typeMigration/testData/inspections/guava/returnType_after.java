import java.util.ArrayList;
import java.util.Optional;

public class Main8 {

  void m() {
    String value = getOptional().orElse(null);
  }

  Optional<String> getOptional() {
    return new ArrayList<String>().stream().map(x -> x + x).filter(String::isEmpty).findFirst();
  }
}