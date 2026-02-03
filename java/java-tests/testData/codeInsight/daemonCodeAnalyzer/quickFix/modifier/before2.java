// "Make 'a' not abstract" "false"
import java.io.*;

abstract class a {
  void f() {
    new <caret>a();
  }
  abstract void f2();
}

