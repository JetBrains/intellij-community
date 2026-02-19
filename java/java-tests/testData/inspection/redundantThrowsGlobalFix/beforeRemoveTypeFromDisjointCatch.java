// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() throws <caret>FileNotFoundException {}
  {
    try {
      f();
    } catch (FileNotFoundException | FileNotFoundException e) {}

    try {
      f();
    } catch (FileNotFoundException | Exception e) {}

    try {
      f();
      f();
    } catch (FileNotFoundException | Exception e) {}
  }
}

class B {
  {
    try {
      new Main().f();
    } catch (FileNotFoundException | FileNotFoundException e) {}

    try {
      new Main().f();
    } catch (FileNotFoundException | Exception e) {}

    try {
      new Main().f();
      new Main().f();
    } catch (FileNotFoundException | Exception e) {}
  }
}
