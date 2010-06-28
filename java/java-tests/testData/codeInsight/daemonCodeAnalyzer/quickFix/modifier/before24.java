// "Make 'a.i' public" "true"
import java.io.*;

class a {
  private int i;
}
class b extends a {
  void f() {
    int p = <caret>i;
  }
}
