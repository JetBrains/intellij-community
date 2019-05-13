import java.util.List;
import java.util.function.Function;

class RunnableGroup<R extends Runnable> implements Runnable {
  public <T> RunnableGroup(List<T> list, Function<T, R> function) {}
  @Override public void run() {}
}

class Usage {

  public static void m(List<Integer> list,
                       Function<Integer, Runnable> function) {
    run(new RunnableGroup<>(list, function));
  }

  static <K extends Runnable> K run(K runnable) { return runnable; }

}