// "Make 'i' static" "true"
import java.io.*;

class a {
  int i;
  static void f() {
    int p = <caret>i;
  }
}
