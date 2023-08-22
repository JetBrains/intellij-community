// "Specify UTF-8 charset" "true"
import java.io.*;

class X {
  void test(String s) {
    byte[] bytes = s.getByt<caret>es();
  }
}