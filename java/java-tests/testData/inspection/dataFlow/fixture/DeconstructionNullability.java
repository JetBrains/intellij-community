class Test {
  static void check(Result<CharSequence> result) {
    switch (result) {
      case Result.Ok(String string) -> {
        if (<warning descr="Condition 'string == null' is always 'false'">string == null</warning>) {}
      }
      case Result.Ok(CharSequence cs) -> {
        if (cs == null) {
          System.out.println("Null");
        }
      }
      case Result.Err(RuntimeException throwable) -> {
        if (throwable == null) {
        }
      }
    }
  }
  public static void main(String[] args) {
    check(Result.ok(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>));
  }
}
sealed interface Result<T> {
  record Ok<R>(R value) implements Result<R> {
    public R get() {
      return value;
    }
  }

  record Err<E>(RuntimeException throwable) implements Result<E> {
    public E get() {
      throw throwable;
    }
  }

  static <OK> Result<OK> ok(OK value) {
    return new Ok<>(value);
  }

  static <ERR> Result<ERR> err(RuntimeException throwable) {
    return new Err<>(throwable);
  }
}
