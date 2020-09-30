// "FileNotFoundException" "true"
import java.io.FileNotFoundException;

class Main {
  public void f() {}
  {
      f();
      f();
  }
}

class B {
  {
      new Main().f();
      new Main().f();
  }
}
