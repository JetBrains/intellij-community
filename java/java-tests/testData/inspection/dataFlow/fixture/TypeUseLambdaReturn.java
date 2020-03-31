import ambiguous.*;

class Test {
  void run1() {
    String result = compute(() -> {
      return Math.random() > 0.5 ? getResult() : getResult2();
    });
    if (result == null) {}
  }
  
  void run() {
    String result = compute(() -> {
      return Math.random() > 0.5 ? getResult() : null;
    });
    if (result == null) {}
  }
  
  native @NotNull String getResult();
  native @Nullable String getResult2();
  public native static <T> T compute(Computable<T> action);
}

interface Computable<T> {
  T compute();
}