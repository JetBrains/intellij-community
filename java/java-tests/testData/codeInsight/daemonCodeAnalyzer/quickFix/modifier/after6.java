// "Make 'i' not static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>int i;
  }
  void f() {
  }
}
