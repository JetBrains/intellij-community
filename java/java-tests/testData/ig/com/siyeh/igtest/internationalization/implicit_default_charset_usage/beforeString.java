// "Specify UTF-8 charset" "true"
import java.io.*;

class X {
  void test(byte[] bytes) {
    String s = new Stri<caret>ng(bytes)
  }
}