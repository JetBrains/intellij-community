// "Make 'a' not final" "true-preview"
import java.io.*;

class a {
  void f() {
    new <caret>a() {};
  }
}
