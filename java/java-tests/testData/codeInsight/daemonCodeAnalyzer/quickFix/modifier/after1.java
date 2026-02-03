// "Make 'a' not abstract" "true-preview"
import java.io.*;

class a {
  void f() {
    new <caret>a();
  }
}

