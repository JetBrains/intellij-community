import java.io.*;
import java.lang.Exception;

class C {
  void m0() throws Exception {
    try (FileReader reader = new FileReader(new File("input.txt"))) {
      reader.read();
    } catch (Exception e) {
      <error descr="Cannot resolve symbol 'reader'">reader</error> = null;
    }
    <error descr="Cannot resolve symbol 'reader'">reader</error> = null;
  }

  void m1() {
    try (final FileReader reader = new FileReader(new File("input.txt"))) {
      reader.read();
    }
    catch (IOException ignore) { }

    try (final FileReader reader = new FileReader(new File("input.txt"))) {
      System.out.println("Try.");
    }
    catch (IOException ignore) { }
  }

  /*void m2() throws IOException {
    try (final FileReader reader = new FileReader(new File("input.txt"))) {
      reader.read();
    }
  }*/
}