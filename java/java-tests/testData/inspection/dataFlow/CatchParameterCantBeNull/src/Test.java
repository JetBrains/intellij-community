import java.io.*;

public class Foo {
  public void foo() {
    try {
      throw new EOFException("aaa");
    } catch (Exception e) {
      if (e == null) {
        System.out.println("Can't be here.");
      }
      if (e instanceof FileNotFoundException) {
        System.out.println("Can't be here.");
      }
    }
  }
}