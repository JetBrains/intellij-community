abstract class Main {

  public interface LifetimeFunction<ELF extends Throwable> {
    int execute() throws ELF;
  }

  public final <E extends Throwable> void foo(final LifetimeFunction<E> action) throws E {
    runSync(() -> {
      action.execute();
      return 42;
    });
  }

  abstract < E1 extends Throwable> void runSync(LifetimeFunction<E1> action) throws E1;
}