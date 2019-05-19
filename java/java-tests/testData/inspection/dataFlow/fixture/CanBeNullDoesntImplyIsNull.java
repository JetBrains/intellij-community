import org.jetbrains.annotations.Nullable;

class DDD {
  int test(boolean t) {
    if (t && <warning descr="Dereference of 'fff()' may produce 'NullPointerException'">fff()</warning>.length == 1) {
      return 0;
    }
    return 1;
  }

  public @Nullable DDD[] fff() {
    return new DDD[8];
  }
}
