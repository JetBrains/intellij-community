import java.io.File;

class Test {
  public interface A<E extends Throwable> {
    Object call() throws E;
  }

  public interface B<E extends Throwable> {
    void call() throws E;
  }

  static <T, E extends Throwable> T method(A<E> lambda) {
    try {
      lambda.call();
    } catch (Throwable e) {
      e.printStackTrace();
    }
    return null;
  }

  static <E extends Throwable> void method(B<E> lambda) {
    try {
      lambda.call();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  static String returns(String s) throws Exception {
    System.out.println(s); return null;
  }

  static void voids(String s) throws Exception {
    System.out.println(s);
  }

  static {

    method(() -> {
      voids("B");
    });
    method(() -> voids("B"));

    method(() -> {
      return new File(returns("A"));
    });
    method(() -> new File(returns("A")));
  }
}
