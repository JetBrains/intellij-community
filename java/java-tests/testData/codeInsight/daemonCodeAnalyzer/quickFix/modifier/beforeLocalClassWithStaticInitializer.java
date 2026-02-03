// "Make 'inner' static" "false"
import java.io.*;

class a {
  void foo() {
    class inner {
      <caret>static {}
    }
  }
}
