import java.util.*;

public class C {
  Object o = new Object() {
    class Inner {
    }

    void foo(List<Inner> inners) {
        for (Inner inner : inners) {
            
        }
    }
  };
}
