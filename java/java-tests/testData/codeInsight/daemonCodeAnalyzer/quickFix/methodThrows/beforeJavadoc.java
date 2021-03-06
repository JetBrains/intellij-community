// "Fix all 'Redundant 'throws' clause' problems in file" "true"
import java.io.*;

class A {
  /**
   * some description
   * @throws IOException
   */
  private void foo() throws <caret>IOException {
  }
}

