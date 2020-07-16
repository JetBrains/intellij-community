// "Fix all 'Redundant 'throws' clause' problems in file" "true"

import java.io.FileNotFoundException;
import java.io.IOException;

class A {
  /**
   * @since 2020.3
   * @author me
   * @throws Exception first exception
   * @throws Exception second exception
   * @throws Exception
   * @throws Exception
   * @throws Exception
   * @throws IOException IO exception 1
   * @throws Exception
   * @throws Exception
   * @throws Exception
   * @throws FileNotFoundException file not found
   * @throws IOException IO exception 2
   */
  void f() throws /* 1 */ E<caret>xception /* 2 */, /* 3 */ Exception /* 4 */, /* 5 */ IOException /* 6 */,
  // seven

    /* 8 */ FileNotFoundException /* 9 */ {  }
}

