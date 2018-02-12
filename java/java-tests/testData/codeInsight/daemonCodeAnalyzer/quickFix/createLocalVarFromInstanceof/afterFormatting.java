// "Insert '(IOException)o' declaration" "true"
import java.io.IOException;

class C {
  void f(Object o) {
    if (o instanceof IOException) {
        IOException o1 = (IOException) o;
        <caret>
    }
  }
}

