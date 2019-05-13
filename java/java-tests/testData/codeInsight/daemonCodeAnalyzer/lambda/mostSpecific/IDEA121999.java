import java.util.function.Supplier;

class LambdaTest {

  static {
    int <warning descr="Variable 'i' is never used">i</warning> = doSync(() -> foo());
    int <warning descr="Variable 'i1' is never used">i1</warning> = doSync(LambdaTest::foo);
  }

  public static <T> T doSync(Supplier<T> <warning descr="Parameter 'block' is never used">block</warning>) {
    return null;
  }

  public static void doSync(Runnable <warning descr="Parameter 'block' is never used">block</warning>) {
  }

  public static int foo() {
    return 0;
  }

}