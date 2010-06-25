// "Make 'i' not static" "true"
import java.io.*;

class a {
  class inner {
    <caret>int i;
  }
  void f() {
  }
}
