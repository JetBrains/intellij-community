// IDEA-316248
package java.lang;

import java.io.PrintStream;

class System {
  public static final PrintStream out = null;
  
  void test() {
    out.println(1);
  }
}