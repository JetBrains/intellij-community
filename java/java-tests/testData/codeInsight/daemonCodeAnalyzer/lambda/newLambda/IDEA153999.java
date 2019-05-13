import java.util.Optional;
import java.util.function.Function;

class OptionTest2 {
  interface Multi {

    String yo();

    default <R> R match(Function<? super T1,? extends R> t1func,
                        Function<? super T2,? extends R> t2func,
                        Function<? super T3,? extends R> t3func) {
      if (this instanceof T1) {
        return t1func.apply((T1) this);
      } else if (this instanceof T2) {
        return t2func.apply((T2) this);
      } else if (this instanceof T3) {
        return t3func.apply((T3) this);
      } else {
        throw new RuntimeException("Whoa!");
      }
    }

    class T1 implements Multi {
      public String yo() {
        return "T1";
      }
    }

    class T2 implements Multi {
      public String yo() {
        return "T2";
      }
    }

    class T3 implements Multi {
      public String yo() {
        return "T3";
      }
    }
  }


  {
    String result = new Multi.T1().match(
      Optional::of,
      Optional::of,
      t3 -> Optional.<Multi>empty()
    ).map(Multi::yo).orElse("Nope");

    assertEquals("T1", result);

    assertEquals("2", new Multi.T2().match(
      t1 -> "1",
      t2 -> "2",
      Multi.T3::yo));
  }

  static void assertEquals(Object o, Object o1) {}
}
