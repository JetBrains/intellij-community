import java.io.*;

class Foo {

  public void read() {
    try {
      final FileInputStream input = new FileInputStream(new File("foo"));
      try {
      }
      finally {
        input.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
    catch (IOException e) {
    }
  }

  public void read2() throws Exception {
    try {
      final FileInputStream input = new FileInputStream(new File("foo"));
    }
    finally {
      System.exit(0);
    }
  }

}