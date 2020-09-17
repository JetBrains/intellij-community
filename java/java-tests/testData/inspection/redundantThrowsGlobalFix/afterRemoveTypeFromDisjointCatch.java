// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() {}
  {
      f();

      try {
      f();
    } catch (Exception e) {}

    try {
      f();
      f();
    } catch (Exception e) {}
  }
}

class B {
  {
      new Main().f();

      try {
      new Main().f();
    } catch (Exception e) {}

    try {
      new Main().f();
      new Main().f();
    } catch (Exception e) {}
  }
}
