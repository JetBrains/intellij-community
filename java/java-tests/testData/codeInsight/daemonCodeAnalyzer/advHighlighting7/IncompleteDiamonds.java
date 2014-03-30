class Test {

  public static void main(String[] args) {
    Box<String> stringBox = new Box<String>("123");

      stringBox.transform(new Fn<String,<error descr="Identifier expected"> </error>>() {});

  }

  static class Box<T> {
    T value;

    Box(T value) {
      this.value = value;
    }

    public <O> Box<O> transform(Fn<T, O> fn) {
      return new Box<O>(fn.apply(value));
    }
  }

  interface Fn<A, B> {
    B apply(A value);
  }
}
