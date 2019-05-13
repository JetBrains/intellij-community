// "Add exception to existing catch clause" "true"
import java.io.File;
import java.io.IOException;

class Test {
  public static void main(String[] args) {
    try {
      new File("path").getCanonicalPath();
    } catch (IndexOutOfBoundsException | IOException e) {
    }
  }
}