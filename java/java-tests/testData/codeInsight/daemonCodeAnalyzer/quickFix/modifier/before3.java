// "Make 'i' not abstract" "false"
import java.io.*;

class a {
  void f() {
    new <caret>i();
  }
}
interface i {
}
