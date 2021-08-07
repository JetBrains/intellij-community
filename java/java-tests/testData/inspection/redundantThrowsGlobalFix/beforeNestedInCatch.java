// "FileNotFoundException" "true"

import java.io.FileNotFoundException;

class Main {
  public void f() throws <caret>FileNotFoundException {}
  {
    try {
      f();
    } catch (FileNotFoundException e1) {
      try {
        f();
      } catch (FileNotFoundException e2) {
        e2.printStackTrace();
      }
    }
  }
}