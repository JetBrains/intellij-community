import java.util.function.Supplier;

class Main {
  private static void boom() {}
  private static <R> void map (Supplier<R> fn) {}
  private static     void map1(Supplier<Runnable> fn) {}

  public static void main(String[] args) {
    Runnable r = () -> {};
    map(() -> (Runnable) Main::boom );
    map(() -> true ? (Runnable) Main::boom : (Runnable) Main::boom );
    map(() -> {
      return true ? (Runnable) Main::boom : (Runnable) Main::boom;
    });

    map(() -> true ? (Runnable) Main::boom : r );
    map(() -> (true ? (Runnable)(Main::boom) : r));

    map(() -> {
      if (true) {
        return (Runnable) Main::boom;
      }
      return (Runnable) Main::boom;
    });

    map1(() -> (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom);
    map1(() -> true ? (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom : (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom);
    map1(() -> {
      return true ? (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom : (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom;
    });
    map1(() ->  true ? (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom : r);
    map1(() -> (true ? (<warning descr="Casting '(Main::boom)' to 'Runnable' is redundant">Runnable</warning>) (Main::boom) : r));
    map1(() -> {
      if (true) {
        return (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom;
      }
      return (<warning descr="Casting 'Main::boom' to 'Runnable' is redundant">Runnable</warning>) Main::boom;
    });
  }
}