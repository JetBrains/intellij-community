// "Add exception to existing catch clause" "true"
import java.io.IOException;

class Test {
  public static void main(String[] args) {
    try {
      throw new IOException<caret>();
    } catch (IndexOutOfBoundsException e) {
    }
  }
}