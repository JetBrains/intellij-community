// "Remove 'IOException' from 'f()' throws list" "true-preview"

import java.io.FileNotFoundException;
import java.io.IOException;

class A {
  /**
   * @since 2020.3
   * @author me
   * @throws Exception first exception
   * @throws Exception second exception
   * @throws FileNotFoundException file not found
   */
  void f() throws /* 1 */ Exception /* 2 */ /* 3 */ /* 4 */ {}
}
