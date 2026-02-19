// "Make 'a' not final" "true-preview"
import java.io.*;

final class a {
  void f() {
    new <caret>a() {};
  }
}
