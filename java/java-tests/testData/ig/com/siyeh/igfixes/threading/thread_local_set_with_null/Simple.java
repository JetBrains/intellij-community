public class Simple {
  public static void main(String[] args) {
    ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
    threadLocal.s<caret>et(null);
  }
}

