// "Add 'IOException' to catch with 'IndexOutOfBoundsException'" "true-preview"
import java.io.File;

class Test {
  public static void main(String[] args) {
    try {
      new File("path").getCanonical<caret>Path();
    } catch (IndexOutOfBoundsException e) {
    }
  }
}