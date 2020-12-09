// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() throws <caret>FileNotFoundException {}
  {
    try {
      f();
    } catch (FileNotFoundException e) {}
  }
}

class B {
  {
    try {
      new Main().f();
    } catch (FileNotFoundException e) {}
  }
}
