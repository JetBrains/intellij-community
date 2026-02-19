// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() throws <caret>FileNotFoundException {}

  {
    try {
      f();
    } catch (FileNotFoundException e) {
    } catch (Exception e) {
    }
  }
}
