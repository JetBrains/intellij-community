import java.io.*;

class UnsupportedFeatures {
  void m() throws Exception {
    Reader r1 = new FileReader("/dev/null");
    try (<error descr="Resource references are not supported at this language level">r1</error>; Reader r2 = new FileReader("/dev/null")) { }
  }
}