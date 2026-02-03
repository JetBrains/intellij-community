import java.util.concurrent.Callable;

class Test {
  public static void main(String[] args) {
    method(() -> {
      if (check(args[0])) {
        return "";
      } else {
        throw new Exception("");
      }
    });
  }

  public static <T> void method(Callable<T> callable) {}
  public static boolean check(String s) throws Exception {
    return true;
  }
}