// "Delete catch for 'java.io.IOException'" "true"
import java.io.*;

class C {
  void m() {
    try {
      int p = 0;
    }
    catch (<caret>IOException | /*somethihg*/ RuntimeException e) {
    }
  }
}
