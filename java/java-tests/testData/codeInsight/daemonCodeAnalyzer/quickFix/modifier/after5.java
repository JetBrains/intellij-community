// "Make 'a' not final" "true"
import java.io.*;

class a {
  void f() {
    new <caret>a() {};
  }
}
