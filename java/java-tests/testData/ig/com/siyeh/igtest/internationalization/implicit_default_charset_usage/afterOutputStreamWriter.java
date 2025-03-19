// "Specify UTF-8 charset" "true"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test(OutputStream os) {
    Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
  }
}