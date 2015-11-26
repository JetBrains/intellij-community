public class Test {
  public static void test() {
    Number n = new Interface2<Integer,Void>() {
      @Override
      public Integer invoke(Void v) {
        return null;
      }

      @Override
      public Integer invokeAnotherTime(Void aVoid) {
        return Integer.valueOf("123");
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