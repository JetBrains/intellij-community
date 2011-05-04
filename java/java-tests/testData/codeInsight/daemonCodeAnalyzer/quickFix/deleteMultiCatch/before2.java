// "Delete catch for 'java.io.FileNotFoundException'" "true"
import java.io.*;

class C {
  void m() {
    try {
      int p = 0;
    }
    catch (EOFException | <caret>FileNotFoundException /*somethihg*/ e) {
    }
  }
}
