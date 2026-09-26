// "Make 'i' not final" "true-preview"
import java.io.*;

final class a {
  void f() {
    final int i = 0;
    <caret>i = 8;
  }
}
