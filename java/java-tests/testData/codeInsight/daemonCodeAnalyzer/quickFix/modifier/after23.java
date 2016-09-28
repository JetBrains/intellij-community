// "Make 'a.i' package-private" "true"
import java.io.*;

class a {
  int i;
}
class b extends a {
  void f() {
    int p = <caret>i;
  }
}
