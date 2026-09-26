
public class OnlyUncheckedWarningCastWithInnerClasses<T> {
  public abstract class Mapper  extends UnaryOperator {
  }
  public abstract class UnaryOperator  implements Function<T, T> {}

  void test(OnlyUncheckedWarningCastWithInnerClasses<? extends CharSequence>.UnaryOperator op) {
    OnlyUncheckedWarningCastWithInnerClasses<? extends CharSequence>.Mapper op1 = (OnlyUncheckedWarningCastWithInnerClasses<?  extends  CharSequence>.Mapper) op;
  }
}

interface Function<T, R> {
  R apply(T t);
}
