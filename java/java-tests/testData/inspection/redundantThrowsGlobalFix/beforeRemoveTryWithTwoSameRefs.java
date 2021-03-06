// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() throws <caret>FileNotFoundException {}
  {
    try {
      f();
      f();
    } catch (FileNotFoundException e) {}
  }
}

class B {
  {
    try {
      new Main().f();
      new Main().f();
    } catch (FileNotFoundException e) {}
  }
}
