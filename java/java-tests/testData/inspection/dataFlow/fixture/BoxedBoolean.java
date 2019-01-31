import org.jetbrains.annotations.*;

class X {
  native @Nullable Boolean readSomething();

  void test() {
    final Boolean x = readSomething();
    if(x == Boolean.TRUE) {}
    else if(x == Boolean.FALSE || x == null) {}
    else {}
  }
}