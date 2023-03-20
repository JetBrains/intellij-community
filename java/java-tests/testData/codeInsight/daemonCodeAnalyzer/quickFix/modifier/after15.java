// "Make 'i' not final" "true-preview"
import java.io.*;

final class a {
  void f() {
    int i = 0;
    <caret>i = 8;
  }
}
