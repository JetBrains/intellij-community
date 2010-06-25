// "Make 'a' not final" "true"
import java.io.*;

final class a {
  void f() {
    new <caret>a() {};
  }
}
