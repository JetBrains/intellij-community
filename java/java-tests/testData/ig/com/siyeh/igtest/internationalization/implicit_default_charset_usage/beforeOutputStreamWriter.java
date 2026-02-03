// "Specify UTF-8 charset" "true"
import java.io.*;

class X {
  void test(OutputStream os) {
    Writer writer = new Ou<caret>tputStreamWriter(os);
  }
}