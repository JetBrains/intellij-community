// "Add 'IOException' to catch with 'IndexOutOfBoundsException'" "true-preview"
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