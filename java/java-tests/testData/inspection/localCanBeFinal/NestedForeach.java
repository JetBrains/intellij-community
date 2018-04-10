import java.util.*;

class NestedForeach {
  void foo(final List<String> list) {
    new Object() {
      void one() {
        new Object() {
          void two() {
            new Object() {
              void three() {
                for (String <warning descr="Variable 's' can have 'final' modifier">s</warning> : list) {
                  System.out.println(s);
                }
              }
            };
          }
        };
      }
    };
  }
}