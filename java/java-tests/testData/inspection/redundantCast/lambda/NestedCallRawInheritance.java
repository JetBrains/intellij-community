import java.util.function.Function;

class Use<T> {
  abstract static class Super<Value> {}

  static class Obj extends Super {
    static final Obj INSTANCE = new Obj();
  }

  <R> R test(Function<Super<T>, R> fn) {
    Obj instance = Obj.INSTANCE;
    return fn.apply((Super<T>) instance);
  }
}