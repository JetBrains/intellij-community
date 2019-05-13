// "Remove 'RuntimeException' from 'f' throws list" "false"
import java.io.*;

class a {
  void f() throws <caret>RuntimeException {
  }
}
