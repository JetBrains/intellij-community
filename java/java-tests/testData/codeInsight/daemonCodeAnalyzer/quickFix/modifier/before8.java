// "Make 'f' not static" "true"
import java.io.*;

class a {
  class inner {
    <caret>static void f() {}
  }
}
