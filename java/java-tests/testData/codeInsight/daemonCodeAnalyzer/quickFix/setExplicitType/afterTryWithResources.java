// "Set variable type to 'FileInputStream'" "true-preview"
import java.io.FileInputStream;
import java.io.FileNotFoundException;

class Demo {
  void test() {
    try (FileInputStream input = new FileInputStream("validation.txt")) {
    } catch (FileNotFoundException e) {
    }
  }
}
