import java.io.*;

class C {
  void testConstructor(String path) {
    File a = <weak_warning descr="Multiple occurrences of 'new File(path)'">new File(path)</weak_warning>;
    File b = <weak_warning descr="Multiple occurrences of 'new File(path)'">new File(path)</weak_warning>;
  }

  void testMethods(String path) throws IOException {
    File f = new File(path);

    boolean a = f.createNewFile();
    boolean b = f.createNewFile();

    boolean c = f.delete();
    boolean d = f.delete();
  }
}