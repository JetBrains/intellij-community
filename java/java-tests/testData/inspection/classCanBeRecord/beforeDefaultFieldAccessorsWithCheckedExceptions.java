// "Convert to a record" "false"
import java.io.*;

class <caret>R {
  final int first;

  private R(int first) {
    this.first = first;
  }

  /**
   * some doc
   */
  int first() throws FileNotFoundException {
    return first;
  }
}