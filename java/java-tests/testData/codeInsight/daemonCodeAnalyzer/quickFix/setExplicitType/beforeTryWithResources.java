// "Set variable type to 'FileInputStream'" "true"
import java.io.FileInputStream;
import java.io.FileNotFoundException;

class Demo {
  void test() {
    try (var<caret> input = new FileInputStream("validation.txt")) {
    } catch (FileNotFoundException e) {
    }
  }
}
