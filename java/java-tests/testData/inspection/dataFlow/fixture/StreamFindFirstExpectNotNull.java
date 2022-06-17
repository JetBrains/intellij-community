import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

class StreamTypeAnnoInlining {
  @Nullable
  private static Object fun(String s) {
    return null;
  }

  void test() {
    Stream.of("a", "b").map(s -> <warning descr="Function may return null, but it's not allowed here">fun(s)</warning>).findFirst();
    Stream.of("a", "b").map(s -> <warning descr="Function may return null, but it's not allowed here">fun(s)</warning>).findAny();
  }

}
