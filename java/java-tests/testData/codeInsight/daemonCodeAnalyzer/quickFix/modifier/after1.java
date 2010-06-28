// "Make 'a' not abstract" "true"
import java.io.*;

class a {
  void f() {
    new <caret>a();
  }
}

