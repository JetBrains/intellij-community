import java.util.*;
import java.util.concurrent.atomic.*;

// IDEA-183217
class Test {
  private AtomicLong timeStamp;

  private void method() {
    if (timeStamp == null) {
      timeStamp.<warning descr="Method invocation 'set' may produce 'java.lang.NullPointerException'">set</warning>(Calendar.getInstance().getTimeInMillis()); // not reported
    }
  }
}