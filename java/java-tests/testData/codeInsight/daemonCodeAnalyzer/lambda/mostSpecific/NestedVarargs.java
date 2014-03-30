import java.util.List;

abstract class StreamMain {
  public abstract  <T> Iterable<T> concat(final Iterable<? extends T>... iterables);
  public abstract  <T> Iterable<T> concat(final List<? extends T>... iterables);

  public final List<String> errorFixesToShow = null;
  public final List<String> inspectionFixesToShow = null;

  void foo() {
    exists(concat(errorFixesToShow, inspectionFixesToShow), "");
  }

  public abstract <T> boolean exists(T[] iterable, T t);
  public abstract <T> boolean exists(Iterable<T> iterable, T t);

}


abstract class StreamMainComplexSecendArgument {
  public abstract  <T> Iterable<T> concat(final Iterable<? extends T>... iterables);
  public abstract  <T> Iterable<T> concat(final List<? extends T>... iterables);


  public final List<String> errorFixesToShow = null;
  public final List<String> inspectionFixesToShow = null;

  void foo() {
    Condition<String> condition = new Condition<String>() {
      @Override
      public boolean value(String s) {
        return false;
      }
    };
    exists(concat(errorFixesToShow, inspectionFixesToShow), condition);
  }

  public abstract <T> boolean exists(T[] iterable, Condition<T> condition);

  public abstract <T> boolean exists(Iterable<T> iterable, Condition<T> condition);


  interface Condition<T> {
    boolean value(T t);
  }
}
