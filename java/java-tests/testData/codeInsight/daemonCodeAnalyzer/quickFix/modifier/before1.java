// "Make 'a' not abstract" "true-preview"
import java.io.*;

abstract class a {
  void f() {
    new <caret>a();
  }
}

