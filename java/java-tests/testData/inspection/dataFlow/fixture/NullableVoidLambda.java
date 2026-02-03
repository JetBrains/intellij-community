import org.jetbrains.annotations.Nullable;

class Example {
  void foo() {
    Runnable runnable = () -> bar();
  }

  @Nullable
  String bar() {
    return null;
  }
}