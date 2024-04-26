import org.jetbrains.annotations.NotNull;

public class Test<R> {

  private R getR() {
    return null;
  }

  public <T extends CharSequence> void main(String[] args, T param) {
      MyResult<T, R> myVariable = getTrMyResult(param);

      System.out.println("Custom(" + myVariable.t() + ", " + myVariable.r() + ")");
  }

    private <T extends CharSequence> @NotNull MyResult<T, R> getTrMyResult(T param) {
        T t = param;
        R r = getR();
        System.out.println();
        MyResult<T, R> myVariable = new MyResult<>(t, r);
        return myVariable;
    }

    private record MyResult<T extends CharSequence, R>(T t, R r) {
    }
}