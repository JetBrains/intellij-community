// "Make 'a.i' static" "true-preview"
import java.io.*;

class a {
  static int i;
  static void f() {
    int p = <caret>i;
  }
}
