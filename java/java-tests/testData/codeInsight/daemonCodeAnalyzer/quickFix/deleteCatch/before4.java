// "Delete catch for 'java.io.IOException'" "true"
import java.io.*;

class c {
  void f() {
    try {
      int id = 0;
      try {

      } catch (Exception e) {

      }
    }
    catch (<caret>IOException e) {

    }
  }
}