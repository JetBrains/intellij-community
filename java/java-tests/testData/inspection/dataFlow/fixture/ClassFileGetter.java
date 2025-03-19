import java.util.*;

class Test {
  void testVersion() {
    Runtime runtime = Runtime.getRuntime();
    Runtime.Version version = runtime.version();
    if (version.build().isPresent()) {
      unknown();
      if (<warning descr="Condition 'version.build().isPresent()' is always 'true'">version.build().isPresent()</warning>) {
      }
      if (<warning descr="Condition 'runtime == Runtime.getRuntime()' is always 'true'">runtime == Runtime.getRuntime()</warning>) {
        
      }
    }
  }
  
  native void unknown();
}