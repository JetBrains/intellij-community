// "Make 'inner' static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>static int i;
  }
  void f() {
  }
}
