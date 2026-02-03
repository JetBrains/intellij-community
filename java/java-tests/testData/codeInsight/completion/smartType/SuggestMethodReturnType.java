import java.lang.StringBuilder;

public class Main {

  public <caret> main() {
    if (equals(2)) {
      return "a";
    } else {
      return new StringBuilder();
    }
  }

}
