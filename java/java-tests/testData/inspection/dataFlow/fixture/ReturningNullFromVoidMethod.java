import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  Void test() {
    return null;
  }

  private @Nullable Void test1() {
    return test2();
  }

  native @Nullable Void test2();
}