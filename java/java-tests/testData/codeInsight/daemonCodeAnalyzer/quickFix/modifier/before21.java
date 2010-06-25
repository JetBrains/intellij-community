// "Make 'f' not static" "true"
import java.io.*;

class a {
  public void f() {}
}
class b extends a {
  <caret>public static void f() {}
}
