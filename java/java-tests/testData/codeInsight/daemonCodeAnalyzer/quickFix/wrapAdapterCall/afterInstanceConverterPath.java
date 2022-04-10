// "Adapt using 'toFile()'" "true"
import java.io.*;
import java.nio.file.*;

class Test {
  void test(Path path) {
    File f = path.toFile();
  }
}