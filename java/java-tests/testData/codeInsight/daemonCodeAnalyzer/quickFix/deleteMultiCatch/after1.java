// "Delete catch for 'java.io.IOException'" "true-preview"
import java.io.*;

class C {
  void m() {
    try {
      int p = 0;
    }
    catch (/*somethihg*/ RuntimeException e) {
    }
  }
}
