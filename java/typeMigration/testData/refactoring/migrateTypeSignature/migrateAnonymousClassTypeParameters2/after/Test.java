public class Test {
  public static void test() {
    Number n = new Interface2<Long, Void>() {
      @Override
      public Long invoke(Void v) {
        return null;
      }

      @Override
      public Long invokeAnotherTime(Void aVoid) {
        return Long.valueOf("123");
      }
    }.invoke(null);
  }

  public interface Interface2<T, V> extends Interface<T, V> {
  }

  public interface Interface<T, V> {
    T invoke(V v);

    T invokeAnotherTime(V v);
  }
}