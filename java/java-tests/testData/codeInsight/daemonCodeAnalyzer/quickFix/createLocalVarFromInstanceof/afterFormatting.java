// "Insert '(IOException)o' declaration" "true-preview"
import java.io.IOException;

class C {
  void f(Object o) {
    if (o instanceof IOException) {
        IOException ioException = (IOException) o;
        <caret>
    }
  }
}

