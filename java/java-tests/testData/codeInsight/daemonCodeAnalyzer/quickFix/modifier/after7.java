// "Make 'inner' static" "true-preview"
import java.io.*;

class a {
  static class inner {
    <caret>static int i;
  }
  void f() {
  }
}
