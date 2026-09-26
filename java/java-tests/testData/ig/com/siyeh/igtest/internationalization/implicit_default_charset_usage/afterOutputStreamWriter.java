// "Add 'StandardCharsets.UTF_8' argument" "true-preview"
import java.io.*;
import java.nio.charset.StandardCharsets;

class X {
  void test(OutputStream os) {
    Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
  }
}