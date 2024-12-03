// "Make 'p' static" "false"
import java.io.*;

class a {
  void m(int p) {
    class C {
      static int P = <caret>p;
    }
  }
}
