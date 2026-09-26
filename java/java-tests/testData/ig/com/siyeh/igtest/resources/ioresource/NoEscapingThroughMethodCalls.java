import java.io.*;

class NoEscapingThroughMethodCalls {

  void escaper(InputStream in) {}

  void test1() throws FileNotFoundException {
    escaper(new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>(""));
  }

  void test2() throws FileNotFoundException {
    class X {
      X(InputStream s) {}
    }
    new X(new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("")) {};
    InputStream s = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("");
    new X(s) {};
  }

  void test3() throws FileNotFoundException {
    InputStream in = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("");
    escaper(in);
  }

  void test4() throws FileNotFoundException {
    final FileInputStream in = new FileInputStream("");
    try {

    } finally {
      org.apache.commons.io.IOUtils.closeQuietly(in);
    }
  }

  public static void c() throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("asd"));
    try {
      writer.write(0);
    } finally {
      writer.close();
    }
  }

  public static void d() throws IOException {
    final FileOutputStream out = new FileOutputStream("asd");
    OutputStreamWriter writer = new OutputStreamWriter(out);
    try {
      writer.write(0);
    } finally {
      writer.close();
    }
  }
}