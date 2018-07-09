// "Remove 'IOException' from 'foo' throws list" "true"
import java.io.*;

class A {
  /**
   * some description
   * @throws IOException
   */
  private void foo() throws <caret>IOException {
  }
}

