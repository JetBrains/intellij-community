// "Make 'inner' static" "true"
import java.io.*;

class a {
  static class inner {
    <caret>static void f() {}
  }
}
