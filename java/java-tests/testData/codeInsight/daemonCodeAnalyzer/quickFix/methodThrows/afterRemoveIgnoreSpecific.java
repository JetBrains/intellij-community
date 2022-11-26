// "Remove 'Exception' from 'f()' throws list" "true-preview"

import java.io.FileNotFoundException;
import java.io.IOException;

class A {
  /**
   * @since 2020.3
   * @author me
   * @throws FileNotFoundException file not found
   * @throws IOException IO exception
   */
  void f() throws /* 1 */  /* 2 */ /* 3 */ IOException /* 4 */ {}
}
