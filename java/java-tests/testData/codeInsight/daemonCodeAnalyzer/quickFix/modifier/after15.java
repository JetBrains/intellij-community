// "Make 'i' not final" "true"
import java.io.*;

final class a {
  void f() {
    int i = 0;
    <caret>i = 8;
  }
}
