// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() {}

  {
    try {
      f();
    } catch (Exception e) {
    }
  }
}
