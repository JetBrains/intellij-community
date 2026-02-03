// "Make 'i' not final" "false"
import java.io.*;

final class a {
  void f() {
    final int i;
    <caret>i = 8;
  }
}
