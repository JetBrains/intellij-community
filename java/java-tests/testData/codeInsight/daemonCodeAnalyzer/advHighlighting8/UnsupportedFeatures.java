import java.io.*;

class UnsupportedFeatures {
  void m() throws Exception {
    Reader r1 = new FileReader("/dev/null");
    try (<error descr="Resource references are not supported at language level '8'">r1</error>; Reader r2 = new FileReader("/dev/null")) { }
  }
}
<error descr="Sealed classes are not supported at language level '8'">sealed</error> interface Cacheable <error descr="Sealed classes are not supported at language level '8'">permits</error> Result {
  default void clear() {
    System.out.println("clearing cache...");
  }
}
<error descr="Sealed classes are not supported at language level '8'">sealed</error> abstract class Result implements Cacheable <error descr="Sealed classes are not supported at language level '8'">permits</error> IntResult {
}
<error descr="Sealed classes are not supported at language level '8'">non-sealed</error> class IntResult extends Result {
}