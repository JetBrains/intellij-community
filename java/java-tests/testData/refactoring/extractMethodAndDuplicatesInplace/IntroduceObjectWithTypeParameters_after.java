import org.jetbrains.annotations.NotNull;

public class Test<R> {

  private R getR() {
    return null;
  }

  public <T extends CharSequence> void main(String[] args, T param) {
      Result<T, R> result = getTrResult(param);

      System.out.println("Custom(" + result.t() + ", " + result.r() + ")");
  }

    private <T extends CharSequence> @NotNull Result<T, R> getTrResult(T param) {
        T t = param;
        R r = getR();
        System.out.println();
        Result<T, R> result = new Result<>(t, r);
        return result;
    }

    private record Result<T extends CharSequence, R>(T t, R r) {
    }
}