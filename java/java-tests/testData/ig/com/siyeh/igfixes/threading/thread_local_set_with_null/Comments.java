public class Simple {
  public static void main(String[] args) {
    ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
    (getThreadLocal/*test1*/(threadLocal))/*test2*/.set<caret>/*test3*/((null)/*test4*/);
  }

  private static ThreadLocal<Integer> getThreadLocal(ThreadLocal<Integer> threadLocal) {
    return threadLocal;
  }
}
