// "Make 'a' not abstract" "true"
import java.io.*;

abstract class a {
  void f() {
    new <caret>a();
  }
}

