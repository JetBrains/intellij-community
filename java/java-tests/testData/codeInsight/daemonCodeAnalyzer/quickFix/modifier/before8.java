// "Make 'f()' not static" "true-preview"
import java.io.*;

class a {
  class inner {
    <caret>static void f() {}
  }
}
