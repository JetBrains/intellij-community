// "Make 'f' public" "true"
import java.io.*;

class a {
  public void f() {
  }
}
class b extends a {
  void <caret>f() {}
}