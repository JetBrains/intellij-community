// "Make 'i' not final" "true"
import java.io.*;

final class a {
  void f() {
    final int i = 0;
    <caret>i = 8;
  }
}
