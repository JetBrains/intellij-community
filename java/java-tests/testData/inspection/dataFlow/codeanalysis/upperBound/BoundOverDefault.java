import codeanalysis.experimental.annotations.DefaultNotNull;
import codeanalysis.experimental.annotations.Nullable;

@DefaultNotNull
class NullnessDemo {
  static <E extends @Nullable Object> void foo(E e) {
  }
  
  void call() {
    foo(null);
  }
}
