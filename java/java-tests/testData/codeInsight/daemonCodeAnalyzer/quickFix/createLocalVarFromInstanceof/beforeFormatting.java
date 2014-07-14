// "Insert '(IOException)o' declaration" "true"
import java.io.IOException;

class C {
  void f(Object o) {
    if (o instanceof IOException) {
      <caret>
    }
  }
}

