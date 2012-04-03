import java.io.*;

public class Foo {
  public void foo() throws IOException {
    if (4 > 3) {
      foo();
    }
  }
}
